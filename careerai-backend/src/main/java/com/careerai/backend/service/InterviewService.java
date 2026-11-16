package com.careerai.backend.service;

import com.careerai.backend.billing.FeatureKey;
import com.careerai.backend.billing.EntitlementService;
import com.careerai.backend.ai.AiPromptService;
import com.careerai.backend.ai.AiPromptService.InterviewContext;
import com.careerai.backend.ai.AiProvider;
import com.careerai.backend.ai.AiResponseParser;
import com.careerai.backend.ai.AiResponseParser.InterviewQuestionDraft;
import com.careerai.backend.ai.AiResponseParser.InterviewReportDraft;
import com.careerai.backend.ai.AiResponseParser.InterviewTurn;
import com.careerai.backend.dto.InterviewAnswerRequest;
import com.careerai.backend.dto.InterviewAnswerResponse;
import com.careerai.backend.dto.InterviewQuestionResponse;
import com.careerai.backend.dto.InterviewReportResponse;
import com.careerai.backend.dto.InterviewReportResponse.QuestionResult;
import com.careerai.backend.dto.InterviewReportResponse.RecommendedSkill;
import com.careerai.backend.dto.InterviewSessionResponse;
import com.careerai.backend.dto.InterviewStartRequest;
import com.careerai.backend.dto.InterviewStatsResponse;
import com.careerai.backend.dto.InterviewSummaryResponse;
import com.careerai.backend.dto.SkillGapItemResponse;
import com.careerai.backend.entity.InterviewAnswer;
import com.careerai.backend.entity.InterviewQuestion;
import com.careerai.backend.entity.InterviewSession;
import com.careerai.backend.entity.Resume;
import com.careerai.backend.entity.User;
import com.careerai.backend.exception.InvalidResumeRequestException;
import com.careerai.backend.exception.InvalidStateException;
import com.careerai.backend.exception.ResourceNotFoundException;
import com.careerai.backend.exception.ResumeNotFoundException;
import com.careerai.backend.repository.InterviewAnswerRepository;
import com.careerai.backend.repository.InterviewQuestionRepository;
import com.careerai.backend.repository.InterviewSessionRepository;
import com.careerai.backend.repository.ProfileRepository;
import com.careerai.backend.ai.AiResponseParser.VoiceEvaluation;
import com.careerai.backend.ai.AiResponseParser.VoiceQuestionEvaluation;
import com.careerai.backend.dto.InterviewSessionResponse.VoiceContext;
import com.careerai.backend.dto.VoiceInterviewResultResponse;
import com.careerai.backend.dto.VoiceTranscriptRequest;
import com.careerai.backend.service.VoiceTranscriptParser.QaPair;
import com.careerai.backend.service.VoiceTranscriptParser.Turn;
import com.careerai.backend.service.AiUsageService.AiOutcome;
import com.careerai.backend.service.CareerContextService.CareerContext;
import com.careerai.backend.service.InterviewFallbackEngine.BankQuestion;
import com.careerai.backend.service.InterviewFallbackEngine.Evaluation;
import com.careerai.backend.service.InterviewFallbackEngine.KeyPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Text-based AI interview: one adaptive question at a time. Each answer is evaluated and the next
 * question generated in a single AI call (free-tier friendly). When the AI is unavailable the
 * interview continues with the deterministic fallback engine; every question and evaluation is
 * marked AI or FALLBACK. Service methods are not transactional so no database connection is held
 * while waiting for the AI provider.
 */
@Service
@RequiredArgsConstructor
public class InterviewService {

    public static final String FEATURE_QUESTION = "INTERVIEW_QUESTION";
    public static final String FEATURE_EVALUATION = "INTERVIEW_EVALUATION";
    public static final String FEATURE_REPORT = "INTERVIEW_REPORT";
    public static final String FEATURE_VOICE_EVALUATION = "INTERVIEW_VOICE_EVALUATION";
    public static final String VOICE_PROVIDER = "VAPI";

    static final int DEFAULT_QUESTION_COUNT = 6;
    private static final Set<String> TECHNICAL_RESULT_TYPES = Set.of("TECHNICAL", "JOB_SPECIFIC", "RESUME");

    private static final TypeReference<List<KeyPoint>> KEY_POINTS = new TypeReference<>() { };
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() { };
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    private final EntitlementService entitlementService;
    private final InterviewSessionRepository sessionRepository;
    private final InterviewQuestionRepository questionRepository;
    private final InterviewAnswerRepository answerRepository;
    private final ResumeService resumeService;
    private final CareerContextService careerContextService;
    private final CareerRequirementService careerRequirementService;
    private final ResumeTextAnalyzer analyzer;
    private final InterviewFallbackEngine fallback;
    private final AiProvider aiProvider;
    private final AiPromptService promptService;
    private final AiResponseParser responseParser;
    private final AiUsageService usageService;
    private final ObjectMapper objectMapper;
    private final VoiceTranscriptParser transcriptParser;
    private final ProfileRepository profileRepository;

    /** A question ready to be stored, from the AI or the fallback engine. */
    private record Draft(String question, String questionType, String topic, String difficulty, String intent,
                         List<KeyPoint> keyPoints, String referenceAnswer, String source) {
    }


    /* ---------- Start ---------- */

    /**
     * One counting point per interview: the start. Voice interviews are checked and reserved here,
     * BEFORE the frontend creates the Simli session or starts the Vapi call; a failed start gives
     * the use back. Answers, completion and reports never count again.
     */
    public InterviewSessionResponse start(User user, InterviewStartRequest request) {
        boolean voiceMode = "VOICE".equalsIgnoreCase(request.mode());
        EntitlementService.Reservation reservation = entitlementService.reserve(user, voiceMode ? FeatureKey.AI_VOICE_INTERVIEW : FeatureKey.AI_TEXT_INTERVIEW);
        try {
            return doStart(user, request, reservation);
        } catch (RuntimeException ex) {
            entitlementService.release(reservation);
            throw ex;
        }
    }

    private InterviewSessionResponse doStart(User user, InterviewStartRequest request, EntitlementService.Reservation reservation) {
        String type = request.type().toUpperCase(Locale.ROOT);
        String difficulty = request.difficulty() == null ? "INTERMEDIATE" : request.difficulty().toUpperCase(Locale.ROOT);
        CareerContext context = careerContextService.build(user);
        String targetRole = request.targetRole() == null || request.targetRole().isBlank() ? context.targetRole() : request.targetRole().strip();

        Resume resume = request.resumeId() == null ? null : resumeService.requireOwnedResume(user, request.resumeId());
        String resumeText = resume == null ? null : resumeService.requireText(resume);
        if ("RESUME".equals(type) && resume == null) {
            throw new InvalidResumeRequestException("Choose a saved resume for a resume-based interview.");
        }

        String jobDescription = null;
        boolean jobGiven = request.jobDescription() != null && !request.jobDescription().isBlank();
        if ("JOB_SPECIFIC".equals(type) || jobGiven) {
            jobDescription = JobMatchService.requireJobDescription(request.jobDescription());
        }

        List<String> focus = request.focusTopics() == null ? List.of() : request.focusTopics().stream()
            .filter(Objects::nonNull).map(String::strip).filter(topic -> !topic.isEmpty()).distinct().limit(10).toList();

        InterviewSession session = new InterviewSession();
        session.setUser(user);
        session.setType(type);
        session.setTargetRole(targetRole);
        session.setDifficulty(difficulty);
        session.setResumeId(resume == null ? null : resume.getId());
        session.setResumeTitle(resume == null ? null : resume.getTitle());
        session.setJobDescription(jobDescription);
        session.setFocusTopicsJson(focus.isEmpty() ? null : objectMapper.writeValueAsString(focus));
        session.setQuestionCount(request.questionCount() == null ? DEFAULT_QUESTION_COUNT : request.questionCount());
        boolean voice = "VOICE".equalsIgnoreCase(request.mode());
        session.setMode(voice ? InterviewSession.Mode.VOICE.name() : InterviewSession.Mode.TEXT.name());
        session.setProvider(voice ? VOICE_PROVIDER : null);
        if (voice) session.setUsagePeriodStart(reservation.periodStart());
        session = sessionRepository.save(session);

        if (voice) {
            // The voice assistant asks the questions; questions and answers are created from the transcript.
            return toSessionResponse(session, null, voiceContext(user, session, context.experienceLevel(), resumeText));
        }

        InterviewSession saved = session;
        AiOutcome<InterviewQuestionDraft> ai = usageService.tryRun(user, FEATURE_QUESTION, () -> responseParser.parseInterviewQuestion(
            aiProvider.generateJson(promptService.interviewFirstQuestion(interviewContext(saved, context, resumeText, List.of(), saved.getQuestionCount() - 1)))));

        Draft draft = ai.succeeded() ? fromAi(ai.value()) : fallbackQuestion(saved, context, resumeText, List.of());
        saveQuestion(saved, 1, draft);

        String notice = ai.succeeded() ? null
            : "AI interviewer temporarily unavailable (" + ai.reason() + ") Questions come from the CareerAI question bank and answers are scored by key-point matching until it is back.";
        return toSessionResponse(saved, notice, null);
    }


    /* ---------- Answer ---------- */

    public InterviewAnswerResponse answer(User user, Long sessionId, InterviewAnswerRequest request) {
        InterviewSession session = requireOwned(user, sessionId);
        if (session.isVoice()) {
            throw new InvalidStateException("Voice interviews are answered in the voice call.");
        }
        if (session.isCompleted()) {
            throw new InvalidStateException("This interview is already completed.");
        }

        List<InterviewQuestion> questions = questionRepository.findBySessionIdOrderBySequenceNumberAsc(session.getId());
        InterviewQuestion current = questions.isEmpty() ? null : questions.get(questions.size() - 1);
        InterviewQuestion target = questionRepository.findByIdAndSessionId(request.questionId(), session.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Question not found"));
        if (current == null || !current.getId().equals(target.getId()) || answerRepository.existsByQuestionId(target.getId())) {
            throw new InvalidStateException("This question has already been answered. Answer the current question instead.");
        }

        String answerText = request.answer().strip();
        int remaining = session.getQuestionCount() - questions.size();
        CareerContext context = careerContextService.build(user);
        String resumeText = loadResumeText(user, session);
        List<String> askedTexts = questions.stream().map(InterviewQuestion::getQuestion).toList();

        AiOutcome<InterviewTurn> ai = usageService.tryRun(user, FEATURE_EVALUATION, () -> responseParser.parseInterviewTurn(
            aiProvider.generateJson(promptService.interviewTurn(
                interviewContext(session, context, resumeText, askedTexts, remaining), current.getQuestion(), answerText)),
            remaining > 0));

        int score;
        List<String> strengths;
        List<String> missing;
        String feedback;
        String ideal;
        boolean followUp;
        String source;
        Draft next = null;
        String notice = null;

        if (ai.succeeded()) {
            InterviewTurn turn = ai.value();
            score = turn.score();
            strengths = turn.strengths();
            missing = turn.missingPoints();
            feedback = turn.feedback();
            ideal = turn.idealAnswer();
            followUp = turn.followUpRequired();
            source = "AI";

            if (remaining > 0 && turn.nextQuestion() != null && !alreadyAsked(turn.nextQuestion().question(), questions)) {
                next = fromAi(turn.nextQuestion());
            }
        } else {
            Evaluation evaluation = fallback.evaluate(answerText, keyPointsOf(current), current.getReferenceAnswer());
            score = evaluation.score();
            strengths = evaluation.strengths();
            missing = evaluation.missingPoints();
            feedback = evaluation.feedback();
            ideal = evaluation.idealAnswer();
            followUp = evaluation.followUpRequired();
            source = "FALLBACK";
            notice = "AI evaluation temporarily unavailable (" + ai.reason() + ") This answer was scored by key-point matching.";
        }

        InterviewAnswer answer = new InterviewAnswer();
        answer.setQuestion(current);
        answer.setAnswerText(answerText);
        answer.setScore(score);
        answer.setSource(source);
        answer.setFeedbackJson(objectMapper.writeValueAsString(feedbackMap(strengths, missing, feedback, ideal, followUp)));
        answerRepository.save(answer);

        InterviewQuestionResponse nextResponse = null;
        if (remaining > 0) {
            if (next == null) {
                next = fallbackQuestion(session, context, resumeText, questions);
                if (notice == null) notice = "The next question comes from the CareerAI question bank.";
            }
            nextResponse = toQuestionResponse(saveQuestion(session, current.getSequenceNumber() + 1, next), null);
        }

        return new InterviewAnswerResponse(
            session.getId(), current.getId(), score, strengths, missing, feedback, ideal, followUp, source,
            nextResponse, questions.size(), session.getQuestionCount(), remaining == 0, notice
        );
    }


    /* ---------- Complete and report ---------- */

    public InterviewReportResponse complete(User user, Long sessionId) {
        InterviewSession session = requireOwned(user, sessionId);
        if (session.isCompleted() && session.getReportJson() != null) {
            return readReport(session);
        }
        if (session.isVoice()) {
            throw new InvalidStateException("Voice interviews are completed when the call transcript is submitted.");
        }

        List<InterviewQuestion> questions = questionRepository.findBySessionIdOrderBySequenceNumberAsc(session.getId());
        Map<Long, InterviewAnswer> answers = answersByQuestion(session.getId());
        if (answers.isEmpty()) {
            throw new InvalidStateException("Answer at least one question before finishing the interview.");
        }

        List<QuestionResult> results = new ArrayList<>();
        for (InterviewQuestion question : questions) {
            InterviewAnswer answer = answers.get(question.getId());
            if (answer == null) continue;
            Map<String, Object> feedback = objectMapper.readValue(answer.getFeedbackJson(), MAP);
            results.add(new QuestionResult(
                question.getSequenceNumber(), question.getQuestion(), question.getQuestionType(), question.getTopic(),
                answer.getAnswerText(), answer.getScore(), (String) feedback.get("feedback"),
                stringList(feedback.get("strengths")), stringList(feedback.get("missingPoints")),
                (String) feedback.get("idealAnswer"), answer.getSource()));
        }

        List<String> strong = topicsWhere(results, true);
        List<String> weak = topicsWhere(results, false);

        AiOutcome<InterviewReportDraft> ai = usageService.tryRun(user, FEATURE_REPORT, () -> responseParser.parseInterviewReport(
            aiProvider.generateJson(promptService.interviewReport(session.getTargetRole(), session.getType(), transcript(results)))));

        ReportParts parts;
        if (ai.succeeded()) {
            InterviewReportDraft draft = ai.value();
            parts = new ReportParts("AI", draft.summary(), draft.strongAreas(), draft.weakAreas(),
                draft.recommendedSkills(), draft.nextSteps(), draft.writtenCommunicationScore(), null);
        } else {
            int overall = averageScore(results);
            parts = new ReportParts("FALLBACK", fallbackSummary(session, overall, results.size(), strong, weak), List.of(), List.of(),
                List.of(), fallbackNextSteps(weak), null,
                "AI report temporarily unavailable (" + ai.reason() + ") Scores come from your answer evaluations; the summary was generated by CareerAI rules.");
        }

        return saveReport(session, buildReport(session, results, parts));
    }

    /** Qualitative report content from the AI or the fallback rules; empty lists mean "use the score-based topics". */
    private record ReportParts(String source, String summary, List<String> strongAreas, List<String> weakAreas,
                               List<String> recommendedSkills, List<String> nextSteps, Integer writtenCommunicationScore,
                               String notice) {
    }

    /** Scores are always computed here from per-question scores (never taken from the AI). */
    private InterviewReportResponse buildReport(InterviewSession session, List<QuestionResult> results, ReportParts parts) {
        int overall = averageScore(results);
        List<QuestionResult> technical = results.stream()
            .filter(result -> TECHNICAL_RESULT_TYPES.contains(result.questionType()) && !fallback.isBehavioralTopic(nullToEmpty(result.topic())))
            .toList();
        Integer technicalScore = technical.isEmpty() ? null
            : (int) Math.round(technical.stream().mapToInt(QuestionResult::score).average().orElse(0));
        String readiness = overall >= 75 ? "READY" : overall >= 55 ? "DEVELOPING" : "NOT_YET";

        List<String> strong = parts.strongAreas().isEmpty() ? topicsWhere(results, true) : parts.strongAreas();
        List<String> weak = parts.weakAreas().isEmpty() ? topicsWhere(results, false) : parts.weakAreas();
        List<String> recommended = parts.recommendedSkills().isEmpty() ? weak : parts.recommendedSkills();

        return new InterviewReportResponse(
            session.getId(), session.getType(), session.modeOrText(), session.getTargetRole(), session.getDifficulty(),
            session.getResumeTitle(), session.getStartedAt(), LocalDateTime.now(), parts.source(), overall, technicalScore,
            // Voice interviews are evaluated on transcript content only: no communication/voice score.
            session.isVoice() ? null : parts.writtenCommunicationScore(), readiness,
            results.size(), session.getQuestionCount(), strong, weak, recommendedSkills(recommended), results,
            parts.summary(), parts.nextSteps(), parts.notice()
        );
    }

    private InterviewReportResponse saveReport(InterviewSession session, InterviewReportResponse report) {
        session.setStatus(InterviewSession.Status.COMPLETED.name());
        session.setCompletedAt(report.completedAt());
        session.setOverallScore(report.overallScore());
        session.setReportJson(objectMapper.writeValueAsString(report));
        sessionRepository.save(session);
        return report;
    }

    private static int averageScore(List<QuestionResult> results) {
        return (int) Math.round(results.stream().mapToInt(QuestionResult::score).average().orElse(0));
    }


    /* ---------- Voice interviews (Vapi transcript ingestion) ---------- */

    /**
     * Ingests the final transcript of a voice call: derives question/answer pairs, evaluates them
     * with ONE AI call (fallback: key-point scoring) and stores the report. Idempotent: a session can
     * only be processed once (atomic IN_PROGRESS -> PROCESSING claim); repeats return the stored result.
     */
    public VoiceInterviewResultResponse ingestVoiceTranscript(User user, Long sessionId, VoiceTranscriptRequest request) {
        InterviewSession session = requireOwned(user, sessionId);
        if (!session.isVoice()) {
            throw new InvalidStateException("This is a text interview; transcripts can only be submitted for voice interviews.");
        }

        if (sessionRepository.transitionStatus(session.getId(), user.getId(),
                InterviewSession.Status.IN_PROGRESS.name(), InterviewSession.Status.PROCESSING.name()) == 0) {
            return existingVoiceResult(requireOwned(user, sessionId));
        }

        try {
            session = requireOwned(user, sessionId);
            List<Turn> turns = transcriptParser.normalize(request.transcript());
            List<QaPair> pairs = transcriptParser.pairs(turns);

            session.setProvider(VOICE_PROVIDER);
            session.setProviderCallId(request.providerCallId() == null || request.providerCallId().isBlank() ? null : request.providerCallId());
            session.setVoiceTranscriptJson(objectMapper.writeValueAsString(turns));

            if (pairs.isEmpty()) {
                // No meaningful answer: no questions, no score, no report.
                if (turns.isEmpty() && session.getUsagePeriodStart() != null) {
                    // Nothing was said at all: the call never connected, so the voice interview is given back.
                    entitlementService.release(user.getId(), FeatureKey.AI_VOICE_INTERVIEW, session.getUsagePeriodStart());
                }
                session.setStatus(InterviewSession.Status.CANCELLED.name());
                session.setCompletedAt(LocalDateTime.now());
                sessionRepository.save(session);
                return cancelledResult(session);
            }

            InterviewReportResponse report = evaluateVoicePairs(user, session, pairs);
            return new VoiceInterviewResultResponse(session.getId(), InterviewSession.Status.COMPLETED.name(),
                report.source().equals("AI") ? "Your voice interview was evaluated." : report.notice(), report);
        } catch (RuntimeException ex) {
            // Release the claim so the client can retry.
            sessionRepository.transitionStatus(sessionId, user.getId(),
                InterviewSession.Status.PROCESSING.name(), InterviewSession.Status.IN_PROGRESS.name());
            throw ex;
        }
    }

    private VoiceInterviewResultResponse existingVoiceResult(InterviewSession session) {
        if (session.isCompleted() && session.getReportJson() != null) {
            return new VoiceInterviewResultResponse(session.getId(), session.getStatus(), "This transcript was already processed.", readReport(session));
        }
        if (InterviewSession.Status.CANCELLED.name().equals(session.getStatus())) {
            return cancelledResult(session);
        }
        throw new InvalidStateException("This interview transcript is already being processed. Try again in a moment.");
    }

    private static VoiceInterviewResultResponse cancelledResult(InterviewSession session) {
        return new VoiceInterviewResultResponse(session.getId(), InterviewSession.Status.CANCELLED.name(),
            "No answers were captured in this call, so no score or report was created.", null);
    }

    private InterviewReportResponse evaluateVoicePairs(User user, InterviewSession session, List<QaPair> pairs) {
        List<String[]> promptPairs = pairs.stream().map(pair -> new String[] { pair.question(), pair.answer() }).toList();
        AiOutcome<VoiceEvaluation> ai = usageService.tryRun(user, FEATURE_VOICE_EVALUATION, () -> responseParser.parseVoiceEvaluation(
            aiProvider.generateJson(promptService.voiceInterviewEvaluation(session.getTargetRole(), session.getType(), session.getDifficulty(), promptPairs)),
            pairs.size()));

        List<QuestionResult> results = new ArrayList<>();
        for (int i = 0; i < pairs.size(); i++) {
            QaPair pair = pairs.get(i);
            String questionType = voiceQuestionType(session.getType(), pair.question());
            List<String> skills = analyzer.findSkills(pair.question());
            String topic = skills.isEmpty() ? null : skills.get(0);
            List<KeyPoint> keyPoints = fallback.genericKeyPoints(questionType);

            InterviewQuestion question = saveQuestion(session, i + 1, new Draft(pair.question(), questionType,
                topic, session.getDifficulty(), "Asked in the voice interview", keyPoints, null, VOICE_PROVIDER));

            int score;
            List<String> strengths;
            List<String> missing;
            String feedback;
            String ideal;
            String source;
            if (ai.succeeded()) {
                VoiceQuestionEvaluation evaluation = ai.value().questions().get(i);
                score = evaluation.score();
                strengths = evaluation.strengths();
                missing = evaluation.missingPoints();
                feedback = evaluation.feedback();
                ideal = evaluation.idealAnswer();
                source = "AI";
            } else {
                Evaluation evaluation = fallback.evaluate(pair.answer(), keyPoints, null);
                score = evaluation.score();
                strengths = evaluation.strengths();
                missing = evaluation.missingPoints();
                feedback = evaluation.feedback();
                ideal = evaluation.idealAnswer();
                source = "FALLBACK";
            }

            InterviewAnswer answer = new InterviewAnswer();
            answer.setQuestion(question);
            answer.setAnswerText(pair.answer());
            answer.setScore(score);
            answer.setSource(source);
            answer.setFeedbackJson(objectMapper.writeValueAsString(feedbackMap(strengths, missing, feedback, ideal, score < 60)));
            answerRepository.save(answer);

            results.add(new QuestionResult(i + 1, pair.question(), questionType, topic, pair.answer(), score,
                feedback, strengths, missing, ideal, source));
        }

        ReportParts parts;
        if (ai.succeeded()) {
            VoiceEvaluation evaluation = ai.value();
            parts = new ReportParts("AI", evaluation.summary(), evaluation.strongAreas(), evaluation.weakAreas(),
                evaluation.recommendedSkills(), evaluation.nextSteps(), null, null);
        } else {
            List<String> strong = topicsWhere(results, true);
            List<String> weak = topicsWhere(results, false);
            parts = new ReportParts("FALLBACK", fallbackSummary(session, averageScore(results), results.size(), strong, weak),
                List.of(), List.of(), List.of(), fallbackNextSteps(weak), null,
                "AI evaluation temporarily unavailable (" + ai.reason() + ") Answers were scored by key-point matching on the transcript.");
        }

        return saveReport(session, buildReport(session, results, parts));
    }

    private static final java.util.regex.Pattern BEHAVIORAL_CUE = java.util.regex.Pattern.compile(
        "(?i)(tell me about a time|describe a (time|situation)|give me an example of a time|conflict|disagree|teamwork|deadline|challenge you faced|weakness|strength)");

    private static String voiceQuestionType(String interviewType, String question) {
        return switch (interviewType) {
            case "TECHNICAL", "HR", "BEHAVIORAL", "RESUME", "JOB_SPECIFIC" -> interviewType;
            default -> BEHAVIORAL_CUE.matcher(question).find() ? "BEHAVIORAL" : "TECHNICAL";
        };
    }

    /** Compact, sanitised variables for the voice assistant (no email, ids, tokens or full documents). */
    private VoiceContext voiceContext(User user, InterviewSession session, String experienceLevel, String resumeText) {
        String firstName = user.getName() == null ? "there" : user.getName().strip().split("\\s+")[0];

        String resumeContext = "";
        if (resumeText != null && !resumeText.isBlank()) {
            List<String> skills = analyzer.findSkills(resumeText);
            Set<String> sections = analyzer.headingsFound(resumeText);
            resumeContext = "Resume \"" + nullToEmpty(session.getResumeTitle()) + "\". Skills: "
                + (skills.isEmpty() ? "not listed" : String.join(", ", skills.stream().limit(15).toList()))
                + ". Sections: " + (sections.isEmpty() ? "not detected" : String.join(", ", sections)) + ".";
        }

        String jobContext = "";
        if (session.getJobDescription() != null) {
            List<String> skills = analyzer.findSkills(session.getJobDescription());
            jobContext = "Key skills: " + (skills.isEmpty() ? "not detected" : String.join(", ", skills.stream().limit(12).toList()))
                + ". Summary: " + limitText(session.getJobDescription(), 900);
        }

        return new VoiceContext(
            variable(firstName, 40),
            variable(session.getTargetRole(), 120),
            variable(session.getType().replace('_', ' ').toLowerCase(Locale.ROOT), 30),
            variable(session.getDifficulty().toLowerCase(Locale.ROOT), 20),
            variable(experienceLevel == null ? "not specified" : experienceLevel, 60),
            variable(String.join(", ", focusTopics(session)), 400),
            variable(resumeContext, 900),
            variable(jobContext, 1200),
            session.getQuestionCount()
        );
    }

    /** Neutralises template delimiters and control characters in dynamic-variable values. */
    static String variable(String value, int max) {
        if (value == null) return "";
        String cleaned = value.replace("{{", " ").replace("}}", " ").replace("{%", " ").replace("%}", " ")
            .replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").strip();
        return limitText(cleaned, max);
    }

    private static String limitText(String value, int max) {
        String text = value.replaceAll("\\s+", " ").strip();
        return text.length() <= max ? text : text.substring(0, max).strip() + "...";
    }

    public InterviewReportResponse report(User user, Long sessionId) {
        InterviewSession session = requireOwned(user, sessionId);
        if (!session.isCompleted() || session.getReportJson() == null) {
            throw new InvalidStateException("This interview has not been completed yet.");
        }
        return readReport(session);
    }


    /* ---------- History and stats ---------- */

    public InterviewSessionResponse get(User user, Long sessionId) {
        InterviewSession session = requireOwned(user, sessionId);
        VoiceContext voice = null;
        if (session.isVoice() && InterviewSession.Status.IN_PROGRESS.name().equals(session.getStatus())) {
            String experience = profileRepository.findByUserId(user.getId()).map(profile -> profile.getExperienceLevel()).orElse(null);
            voice = voiceContext(user, session, experience, loadResumeText(user, session));
        }
        return toSessionResponse(session, null, voice);
    }

    public List<InterviewSummaryResponse> history(User user) {
        return sessionRepository.findTop50ByUserIdOrderByStartedAtDesc(user.getId()).stream()
            .map(session -> new InterviewSummaryResponse(session.getId(), session.getType(), session.modeOrText(),
                session.getProvider(), session.getTargetRole(),
                session.getDifficulty(), session.getStatus(), session.getQuestionCount(), session.getOverallScore(),
                session.getStartedAt(), session.getCompletedAt()))
            .toList();
    }

    public InterviewStatsResponse stats(User user) {
        List<InterviewSession> completed = sessionRepository
            .findByUserIdAndStatusOrderByCompletedAtDesc(user.getId(), InterviewSession.Status.COMPLETED.name());
        List<Integer> scores = completed.stream().map(InterviewSession::getOverallScore).filter(Objects::nonNull).toList();
        return new InterviewStatsResponse(
            completed.size(),
            scores.isEmpty() ? null : (int) Math.round(scores.stream().mapToInt(Integer::intValue).average().orElse(0)),
            scores.stream().max(Integer::compare).orElse(null),
            scores.isEmpty() ? null : scores.get(0),
            completed.isEmpty() ? null : completed.get(0).getCompletedAt()
        );
    }


    /* ---------- Question planning ---------- */

    /** Topics that fit this interview and have not been asked yet, most relevant first. */
    private List<String> plannedTopics(InterviewSession session, CareerContext context, String resumeText, List<InterviewQuestion> asked) {
        List<String> technical = new ArrayList<>();
        context.weakSkills().stream().map(SkillGapItemResponse::name).forEach(technical::add);
        careerRequirementService.findByTitle(session.getTargetRole())
            .orElse(careerRequirementService.defaultRole())
            .requiredSkills().forEach(skill -> technical.add(skill.name()));
        technical.addAll(context.unassessedSkills());

        List<String> topics = new ArrayList<>(focusTopics(session));
        switch (session.getType()) {
            case "TECHNICAL" -> topics.addAll(technical);
            case "RESUME" -> {
                topics.addAll(analyzer.findSkills(nullToEmpty(resumeText)));
                topics.addAll(InterviewFallbackEngine.BEHAVIORAL_TOPICS);
            }
            case "JOB_SPECIFIC" -> {
                topics.addAll(analyzer.findSkills(nullToEmpty(session.getJobDescription())));
                topics.addAll(analyzer.findSkills(nullToEmpty(resumeText)));
                topics.addAll(InterviewFallbackEngine.BEHAVIORAL_TOPICS);
            }
            case "HR" -> {
                topics.addAll(InterviewFallbackEngine.HR_TOPICS);
                topics.addAll(InterviewFallbackEngine.BEHAVIORAL_TOPICS);
            }
            case "BEHAVIORAL" -> topics.addAll(InterviewFallbackEngine.BEHAVIORAL_TOPICS);
            default -> { // MIXED: alternate technical and behavioral topics
                List<String> behavioral = InterviewFallbackEngine.BEHAVIORAL_TOPICS;
                for (int i = 0; i < Math.max(technical.size(), behavioral.size()); i++) {
                    if (i < technical.size()) topics.add(technical.get(i));
                    if (i < behavioral.size()) topics.add(behavioral.get(i));
                }
            }
        }

        Set<String> askedTopics = new HashSet<>();
        asked.forEach(question -> askedTopics.add(nullToEmpty(question.getTopic()).toLowerCase(Locale.ROOT)));

        Set<String> seen = new HashSet<>();
        List<String> result = new ArrayList<>();
        for (String topic : topics) {
            String key = topic.toLowerCase(Locale.ROOT);
            if (seen.add(key) && !askedTopics.contains(key)) result.add(topic);
        }
        return result;
    }

    private Draft fallbackQuestion(InterviewSession session, CareerContext context, String resumeText, List<InterviewQuestion> asked) {
        Set<String> askedFingerprints = new HashSet<>();
        asked.forEach(question -> askedFingerprints.add(InterviewFallbackEngine.fingerprint(question.getQuestion())));

        List<String> topics = new ArrayList<>(plannedTopics(session, context, resumeText, asked));
        topics.addAll(InterviewFallbackEngine.BEHAVIORAL_TOPICS); // last resort, never empty

        String templateType = switch (session.getType()) {
            case "RESUME", "JOB_SPECIFIC" -> session.getType();
            default -> "TECHNICAL";
        };

        for (String topic : topics) {
            BankQuestion bank = fallback.questionFor(topic, templateType, session.getTargetRole(), askedFingerprints);
            if (!askedFingerprints.contains(InterviewFallbackEngine.fingerprint(bank.question()))) {
                return new Draft(bank.question(), bank.questionType(), bank.topic(), session.getDifficulty(),
                    "Evaluate " + bank.topic(), bank.keyPoints(), bank.referenceAnswer(), "FALLBACK");
            }
        }

        BankQuestion last = fallback.questionFor("Problem solving", "BEHAVIORAL", session.getTargetRole(), Set.of());
        return new Draft(last.question(), last.questionType(), last.topic(), session.getDifficulty(), "Evaluate problem solving",
            last.keyPoints(), last.referenceAnswer(), "FALLBACK");
    }

    private InterviewContext interviewContext(InterviewSession session, CareerContext context, String resumeText,
                                              List<String> askedQuestions, int remaining) {
        boolean useResume = Set.of("RESUME", "JOB_SPECIFIC", "MIXED").contains(session.getType());
        List<InterviewQuestion> asked = session.getId() == null ? List.of()
            : questionRepository.findBySessionIdOrderBySequenceNumberAsc(session.getId());
        return new InterviewContext(
            session.getType(), session.getTargetRole(), session.getDifficulty(),
            candidateContext(context),
            useResume ? resumeText : null,
            session.getJobDescription(),
            plannedTopics(session, context, resumeText, asked).stream().limit(5).toList(),
            askedQuestions,
            remaining
        );
    }

    /** Only interview-relevant facts: no name, plan or other personal data. */
    private static String candidateContext(CareerContext context) {
        StringBuilder text = new StringBuilder();
        if (context.experienceLevel() != null) text.append("Experience level: ").append(context.experienceLevel()).append('\n');
        if (!context.strongSkills().isEmpty()) {
            text.append("Strong in practice: ").append(String.join(", ", context.strongSkills().stream().map(SkillGapItemResponse::name).toList())).append('\n');
        }
        if (!context.weakSkills().isEmpty()) {
            text.append("Weaker in practice: ").append(String.join(", ", context.weakSkills().stream().map(SkillGapItemResponse::name).toList())).append('\n');
        }
        if (!context.unassessedSkills().isEmpty()) {
            text.append("Not yet assessed: ").append(String.join(", ", context.unassessedSkills())).append('\n');
        }
        return text.isEmpty() ? "No practice history yet." : text.toString().strip();
    }


    /* ---------- helpers ---------- */

    private InterviewSession requireOwned(User user, Long id) {
        return sessionRepository.findByIdAndUserId(id, user.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Interview not found"));
    }

    private String loadResumeText(User user, InterviewSession session) {
        if (session.getResumeId() == null) return null;
        try {
            return resumeService.requireText(resumeService.requireOwnedResume(user, session.getResumeId()));
        } catch (ResumeNotFoundException | InvalidResumeRequestException ex) {
            return null; // the resume was deleted or emptied after the interview started
        }
    }

    private Draft fromAi(InterviewQuestionDraft draft) {
        return new Draft(draft.question(), draft.questionType(), draft.topic(), draft.difficulty(), draft.intent(),
            InterviewFallbackEngine.keyPointsFromLabels(draft.expectedKeyPoints()), null, "AI");
    }

    private InterviewQuestion saveQuestion(InterviewSession session, int sequence, Draft draft) {
        InterviewQuestion question = new InterviewQuestion();
        question.setSession(session);
        question.setSequenceNumber(sequence);
        question.setQuestion(draft.question());
        question.setQuestionType(draft.questionType());
        question.setTopic(truncate(draft.topic(), 80));
        question.setDifficulty(truncate(draft.difficulty(), 20));
        question.setIntent(truncate(draft.intent(), 300));
        question.setKeyPointsJson(objectMapper.writeValueAsString(draft.keyPoints()));
        question.setReferenceAnswer(draft.referenceAnswer());
        question.setSource(draft.source());
        return questionRepository.save(question);
    }

    private boolean alreadyAsked(String question, List<InterviewQuestion> asked) {
        String fingerprint = InterviewFallbackEngine.fingerprint(question);
        return asked.stream().anyMatch(item -> InterviewFallbackEngine.fingerprint(item.getQuestion()).equals(fingerprint));
    }

    private List<KeyPoint> keyPointsOf(InterviewQuestion question) {
        return question.getKeyPointsJson() == null ? List.of() : objectMapper.readValue(question.getKeyPointsJson(), KEY_POINTS);
    }

    private List<String> focusTopics(InterviewSession session) {
        return session.getFocusTopicsJson() == null ? List.of() : objectMapper.readValue(session.getFocusTopicsJson(), STRINGS);
    }

    private Map<Long, InterviewAnswer> answersByQuestion(Long sessionId) {
        Map<Long, InterviewAnswer> map = new HashMap<>();
        answerRepository.findBySessionId(sessionId).forEach(answer -> map.put(answer.getQuestion().getId(), answer));
        return map;
    }

    private static Map<String, Object> feedbackMap(List<String> strengths, List<String> missing, String feedback, String ideal, boolean followUp) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("strengths", strengths);
        map.put("missingPoints", missing);
        map.put("feedback", feedback);
        map.put("idealAnswer", ideal);
        map.put("followUpRequired", followUp);
        return map;
    }

    private InterviewSessionResponse toSessionResponse(InterviewSession session, String notice, VoiceContext voiceContext) {
        List<InterviewQuestion> questions = questionRepository.findBySessionIdOrderBySequenceNumberAsc(session.getId());
        Map<Long, InterviewAnswer> answers = answersByQuestion(session.getId());
        List<InterviewQuestionResponse> items = questions.stream()
            .map(question -> toQuestionResponse(question, answers.get(question.getId())))
            .toList();
        InterviewQuestionResponse current = session.isCompleted() ? null
            : items.stream().filter(item -> item.evaluation() == null).reduce((first, second) -> second).orElse(null);

        return new InterviewSessionResponse(
            session.getId(), session.getType(), session.modeOrText(), session.getProvider(), session.getTargetRole(),
            session.getDifficulty(), session.getStatus(),
            session.getQuestionCount(), answers.size(), session.getResumeId(), session.getResumeTitle(),
            session.getJobDescription() != null, focusTopics(session), session.getStartedAt(), session.getCompletedAt(),
            session.getOverallScore(), current, items, voiceContext, notice
        );
    }

    private InterviewQuestionResponse toQuestionResponse(InterviewQuestion question, InterviewAnswer answer) {
        InterviewQuestionResponse.Evaluation evaluation = null;
        if (answer != null) {
            Map<String, Object> feedback = objectMapper.readValue(answer.getFeedbackJson(), MAP);
            evaluation = new InterviewQuestionResponse.Evaluation(
                answer.getAnswerText(), answer.getScore(), stringList(feedback.get("strengths")),
                stringList(feedback.get("missingPoints")), (String) feedback.get("feedback"),
                (String) feedback.get("idealAnswer"), Boolean.TRUE.equals(feedback.get("followUpRequired")),
                answer.getSource(), answer.getAnsweredAt());
        }
        return new InterviewQuestionResponse(question.getId(), question.getSequenceNumber(), question.getQuestion(),
            question.getQuestionType(), question.getTopic(), question.getDifficulty(), question.getIntent(),
            question.getSource(), evaluation);
    }

    private InterviewReportResponse readReport(InterviewSession session) {
        return objectMapper.readValue(session.getReportJson(), InterviewReportResponse.class);
    }

    private static List<String> topicsWhere(List<QuestionResult> results, boolean strong) {
        Set<String> topics = new LinkedHashSet<>();
        for (QuestionResult result : results) {
            if (result.topic() == null) continue;
            if (strong ? result.score() >= 75 : result.score() < 60) topics.add(result.topic());
        }
        return List.copyOf(topics).stream().limit(5).toList();
    }

    private List<RecommendedSkill> recommendedSkills(List<String> names) {
        return names.stream().distinct().limit(5).map(name -> new RecommendedSkill(name,
            careerRequirementService.findSkill(name)
                .filter(CareerRequirementService.SkillDefinition::isPracticable)
                .map(skill -> careerRequirementService.practiceRoute(skill, CareerRequirementService.DEFAULT_TRACK_ID))
                .orElse(null)))
            .toList();
    }

    private static String transcript(List<QuestionResult> results) {
        StringBuilder text = new StringBuilder();
        for (QuestionResult result : results) {
            text.append("Q").append(result.sequenceNumber()).append(" [").append(result.questionType()).append(", ")
                .append(nullToEmpty(result.topic())).append("] ").append(result.question()).append('\n')
                .append("Answer: ").append(result.answer().length() > 800 ? result.answer().substring(0, 800) + "..." : result.answer()).append('\n')
                .append("Score: ").append(result.score()).append('\n');
            if (!result.missingPoints().isEmpty()) {
                text.append("Missing: ").append(String.join("; ", result.missingPoints())).append('\n');
            }
            text.append('\n');
        }
        return text.toString();
    }

    private static String fallbackSummary(InterviewSession session, int overall, int answered, List<String> strong, List<String> weak) {
        StringBuilder text = new StringBuilder("You answered ").append(answered).append(" question").append(answered == 1 ? "" : "s")
            .append(" in this ").append(session.getType().toLowerCase(Locale.ROOT).replace('_', ' '))
            .append(" interview for ").append(session.getTargetRole()).append(" with an average score of ").append(overall).append("%.");
        if (!strong.isEmpty()) text.append(" You did well on ").append(String.join(", ", strong)).append('.');
        if (!weak.isEmpty()) text.append(" Focus next on ").append(String.join(", ", weak)).append('.');
        return text.toString();
    }

    private static List<String> fallbackNextSteps(List<String> weak) {
        List<String> steps = new ArrayList<>();
        weak.stream().limit(2).forEach(topic -> steps.add("Practise " + topic + " and review the model answers above."));
        steps.add("Answer each question with a definition, how it works, and an example from your own projects.");
        steps.add("Retry the interview after practising to track your improvement.");
        return steps;
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object value) {
        return value instanceof List<?> list ? (List<String>) list : List.of();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
