package com.careerai.backend.service;

import com.careerai.backend.billing.FeatureKey;
import com.careerai.backend.billing.EntitlementService;
import com.careerai.backend.ai.AiPromptService;
import com.careerai.backend.ai.AiProvider;
import com.careerai.backend.ai.AiResponseParser;
import com.careerai.backend.ai.AiResponseParser.CoachReply;
import com.careerai.backend.dto.CoachConversationResponse;
import com.careerai.backend.dto.CoachHistoryResponse;
import com.careerai.backend.dto.CoachMessageResponse;
import com.careerai.backend.dto.CoachMessageResponse.SuggestedAction;
import com.careerai.backend.dto.CoachReplyResponse;
import com.careerai.backend.dto.PlanTaskResponse;
import com.careerai.backend.dto.SkillGapItemResponse;
import com.careerai.backend.entity.CoachConversation;
import com.careerai.backend.entity.CoachMessage;
import com.careerai.backend.entity.User;
import com.careerai.backend.exception.ResourceNotFoundException;
import com.careerai.backend.repository.CoachConversationRepository;
import com.careerai.backend.repository.CoachMessageRepository;
import com.careerai.backend.service.AiUsageService.AiOutcome;
import com.careerai.backend.service.CareerContextService.CareerContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * AI Coach: persistent conversations per user, one AI call per user message with a bounded
 * memory window plus compact CareerAI context, and a deterministic fallback built from
 * Skill Gap, My Plan and Performance when the AI is unavailable.
 */
@Service
@RequiredArgsConstructor
public class CoachService {

    public static final String FEATURE = "COACH_MESSAGE";
    public static final String DEFAULT_TITLE = "New conversation";
    public static final String FALLBACK_NOTICE =
        "AI Coach is temporarily unavailable. Here is a recommendation based on your CareerAI progress.";

    /** Previous messages sent to the AI with each request (bounded memory). */
    static final int MEMORY_WINDOW = 12;

    /** Pages the Coach may link to (path part; practice routes may carry a ?topic= query). */
    private static final Set<String> ALLOWED_PATHS = Set.of(
        "/practice/technical", "/practice/aptitude", "/practice/coding", "/practice/sql", "/practice/system-design",
        "/career/skill-gap", "/my-plan", "/performance", "/ai-interview/setup", "/question-bank", "/career-tracks",
        "/resume/builder", "/resume/ats-analyzer", "/resume/job-match", "/resume/interview-preparation", "/dashboard"
    );

    private static final TypeReference<List<SuggestedAction>> ACTION_LIST = new TypeReference<>() { };

    private final EntitlementService entitlementService;
    private final CoachConversationRepository conversationRepository;
    private final CoachMessageRepository messageRepository;
    private final CareerContextService careerContextService;
    private final AiProvider aiProvider;
    private final AiPromptService promptService;
    private final AiResponseParser responseParser;
    private final AiUsageService usageService;
    private final ObjectMapper objectMapper;


    /* ---------- Conversations ---------- */

    public CoachConversationResponse create(User user, String title) {
        CoachConversation conversation = new CoachConversation();
        conversation.setUser(user);
        conversation.setTitle(title == null || title.isBlank() ? DEFAULT_TITLE : truncate(title.strip(), 120));
        return toResponse(conversationRepository.save(conversation));
    }

    @Transactional(readOnly = true)
    public List<CoachConversationResponse> list(User user) {
        return conversationRepository.findTop50ByUserIdOrderByUpdatedAtDesc(user.getId()).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public CoachHistoryResponse history(User user, Long conversationId) {
        CoachConversation conversation = requireOwned(user, conversationId);
        List<CoachMessageResponse> messages = messageRepository.findByConversationIdOrderByIdAsc(conversation.getId()).stream()
            .map(this::toMessage)
            .toList();
        return new CoachHistoryResponse(toResponse(conversation), messages);
    }

    @Transactional
    public void delete(User user, Long conversationId) {
        CoachConversation conversation = requireOwned(user, conversationId);
        messageRepository.deleteByConversationId(conversation.getId());
        conversationRepository.delete(conversation);
    }


    /* ---------- Messages ---------- */

    /** Not transactional: no database connection is held while the AI provider is called. */
    public CoachReplyResponse sendMessage(User user, Long conversationId, String text) {
        // Counted once per answered message (AI or fallback reply); a failed request gives the use back.
        return entitlementService.consume(user, FeatureKey.AI_COACH_MESSAGE, () -> doSendMessage(user, conversationId, text));
    }

    private CoachReplyResponse doSendMessage(User user, Long conversationId, String text) {
        CoachConversation conversation = requireOwned(user, conversationId);
        String message = text.strip();

        // Memory window: the latest messages before this one, oldest first.
        List<CoachMessage> recent = new ArrayList<>(messageRepository
            .findByConversationIdOrderByIdDesc(conversation.getId(), PageRequest.of(0, MEMORY_WINDOW)));
        Collections.reverse(recent);

        CoachMessage userMessage = saveMessage(conversation, CoachMessage.Role.USER, message, null, List.of());
        if (DEFAULT_TITLE.equals(conversation.getTitle())) {
            conversation.setTitle(truncate(message.replaceAll("\\s+", " "), 60));
        }

        CareerContext context = careerContextService.build(user);
        List<String> routes = suggestedRoutes(context);

        List<String[]> history = recent.stream()
            .map(item -> new String[] { item.getRole() == CoachMessage.Role.USER ? "Learner" : "Coach", item.getContent() })
            .toList();

        AiOutcome<CoachReply> ai = usageService.tryRun(user, FEATURE, () -> responseParser.parseCoachReply(
            aiProvider.generateJson(promptService.coachReply(context.toPromptText(), history, message, routes))));

        String replyText;
        List<SuggestedAction> actions;
        String source;

        if (ai.succeeded()) {
            replyText = ai.value().message();
            actions = ai.value().actions().stream()
                .filter(action -> isAllowedRoute(action.route()))
                .map(action -> new SuggestedAction(action.label(), action.route()))
                .toList();
            source = "AI";
        } else {
            replyText = fallbackReply(message, context);
            actions = fallbackActions(message, context);
            source = "FALLBACK";
        }

        CoachMessage reply = saveMessage(conversation, CoachMessage.Role.ASSISTANT, replyText, source, actions);
        // Bumps updatedAt (and saves a title taken from the first message).
        conversation.setUpdatedAt(reply.getCreatedAt());
        CoachConversation saved = conversationRepository.save(conversation);

        return new CoachReplyResponse(
            toResponse(saved),
            toMessage(userMessage),
            toMessage(reply),
            replyText,
            actions,
            context.hasPersonalData(),
            source,
            "FALLBACK".equals(source) ? FALLBACK_NOTICE : null
        );
    }


    /* ---------- Deterministic fallback (SkillGap + My Plan + Performance) ---------- */

    String fallbackReply(String message, CareerContext ctx) {
        String text = message.toLowerCase(Locale.ROOT);
        List<PlanTaskResponse> open = ctx.todayTasks().stream().filter(task -> !task.completed()).limit(3).toList();
        SkillGapItemResponse weakest = ctx.weakSkills().isEmpty() ? null : ctx.weakSkills().get(0);
        StringBuilder reply = new StringBuilder();

        if (text.matches(".*\\b(ready|readiness)\\b.*")) {
            reply.append(ctx.readiness() == null
                ? "Your readiness for " + ctx.targetRole() + " has not been measured yet. Take a few practice tests on the required skills so CareerAI can calculate it."
                : "Your readiness for " + ctx.targetRole() + " is " + ctx.readiness() + "%, based on " + ctx.assessedSkills()
                    + " of " + ctx.requiredSkills() + " required skills you have practised.");
            if (!ctx.unassessedSkills().isEmpty()) {
                reply.append("\n\nNot assessed yet: ").append(String.join(", ", ctx.unassessedSkills()))
                    .append(". Assessing them gives a more complete picture.");
            }
        } else if (text.matches(".*\\b(weak|gap|why|improve)\\b.*")) {
            if (ctx.weakSkills().isEmpty()) {
                reply.append("No skill is currently flagged as weak for ").append(ctx.targetRole()).append('.');
                if (!ctx.unassessedSkills().isEmpty()) {
                    reply.append(" These skills have not been assessed yet: ").append(String.join(", ", ctx.unassessedSkills())).append('.');
                }
            } else {
                reply.append("Skills that need the most work for ").append(ctx.targetRole()).append(":\n");
                ctx.weakSkills().forEach(skill -> reply.append("- ").append(skill.name())
                    .append(skill.score() == null ? "" : " (" + skill.score() + "% in practice)").append('\n'));
                reply.append("\nThese come from your practice scores. Short, focused practice on the lowest one first raises readiness fastest.");
            }
        } else if (text.matches(".*\\b(week|weekly|schedule)\\b.*")) {
            List<String> focus = new ArrayList<>();
            ctx.weakSkills().forEach(skill -> focus.add(skill.name()));
            focus.addAll(ctx.unassessedSkills());
            if (focus.isEmpty()) focus.add(ctx.targetRole() + " fundamentals");
            reply.append("A simple plan for this week:\n");
            String[] days = { "Mon", "Tue", "Wed", "Thu", "Fri" };
            for (int i = 0; i < days.length; i++) {
                reply.append(days[i]).append(": ").append(focus.get(i % focus.size())).append(" practice\n");
            }
            reply.append("Weekend: a mock interview and a review of your mistakes.");
        } else if (text.matches(".*\\binterview.*")) {
            reply.append(ctx.interviewsCompleted() == 0
                ? "You have not completed a mock interview yet. Start a text interview for " + ctx.targetRole() + " to see where you stand."
                : "You have completed " + ctx.interviewsCompleted() + " mock interviews (average " + ctx.averageInterviewScore()
                    + "%, last " + ctx.lastInterviewScore() + "%).");
            if (weakest != null) reply.append(" Before the next one, revise ").append(weakest.name()).append('.');
        } else {
            reply.append(open.isEmpty()
                ? "Today's plan is complete. Good work!"
                : "For today, focus on:\n" + String.join("\n", open.stream()
                    .map(task -> "- " + task.title() + " (" + task.durationMinutes() + " min)").toList()));
            if (weakest != null) {
                reply.append("\n\n").append(weakest.name()).append(" is your weakest skill")
                    .append(weakest.score() == null ? "" : " at " + weakest.score() + "%").append(", so give it the most attention.");
            }
            if (ctx.readiness() != null) {
                reply.append("\nCurrent readiness for ").append(ctx.targetRole()).append(": ").append(ctx.readiness()).append("%.");
            }
        }

        return reply.toString().strip();
    }

    private List<SuggestedAction> fallbackActions(String message, CareerContext ctx) {
        Set<SuggestedAction> actions = new LinkedHashSet<>();
        if (!ctx.weakSkills().isEmpty() && ctx.weakSkills().get(0).practiceRoute() != null) {
            actions.add(new SuggestedAction("Practice " + ctx.weakSkills().get(0).name(), ctx.weakSkills().get(0).practiceRoute()));
        }
        if (message.toLowerCase(Locale.ROOT).contains("interview")) {
            actions.add(new SuggestedAction("Start Interview", "/ai-interview/setup"));
        }
        actions.add(new SuggestedAction("Open My Plan", "/my-plan"));
        actions.add(new SuggestedAction("Open Skill Gap", "/career/skill-gap"));
        return actions.stream().filter(action -> isAllowedRoute(action.route())).limit(3).toList();
    }


    /* ---------- helpers ---------- */

    /** Routes the AI may suggest: the learner's own practice/plan routes plus the standard pages. */
    private List<String> suggestedRoutes(CareerContext ctx) {
        Set<String> routes = new LinkedHashSet<>();
        ctx.weakSkills().stream().map(SkillGapItemResponse::practiceRoute).forEach(routes::add);
        ctx.todayTasks().stream().map(PlanTaskResponse::route).forEach(routes::add);
        routes.addAll(List.of("/practice/technical?topic=Java", "/practice/aptitude", "/practice/coding", "/practice/sql",
            "/practice/system-design", "/career/skill-gap", "/my-plan", "/performance", "/ai-interview/setup",
            "/resume/ats-analyzer", "/question-bank"));
        routes.removeIf(route -> route == null || !isAllowedRoute(route));
        return List.copyOf(routes);
    }

    static boolean isAllowedRoute(String route) {
        if (route == null || route.contains("//") || route.contains("..")) return false;
        int query = route.indexOf('?');
        String path = query < 0 ? route : route.substring(0, query);
        if (!ALLOWED_PATHS.contains(path)) return false;
        return query < 0 || (path.startsWith("/practice/") && route.substring(query).matches("\\?topic=[A-Za-z0-9%._\\-]{1,60}"));
    }

    private CoachConversation requireOwned(User user, Long id) {
        return conversationRepository.findByIdAndUserId(id, user.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
    }

    private CoachMessage saveMessage(CoachConversation conversation, CoachMessage.Role role, String content,
                                     String source, List<SuggestedAction> actions) {
        CoachMessage message = new CoachMessage();
        message.setConversation(conversation);
        message.setRole(role);
        message.setContent(content);
        message.setSource(source);
        message.setActionsJson(actions.isEmpty() ? null : objectMapper.writeValueAsString(actions));
        return messageRepository.save(message);
    }

    private CoachConversationResponse toResponse(CoachConversation conversation) {
        return new CoachConversationResponse(
            conversation.getId(), conversation.getTitle(),
            messageRepository.countByConversationId(conversation.getId()),
            conversation.getCreatedAt(), conversation.getUpdatedAt()
        );
    }

    private CoachMessageResponse toMessage(CoachMessage message) {
        List<SuggestedAction> actions = message.getActionsJson() == null
            ? List.of()
            : objectMapper.readValue(message.getActionsJson(), ACTION_LIST);
        return new CoachMessageResponse(message.getId(), message.getRole().name(), message.getContent(),
            message.getSource(), actions, message.getCreatedAt());
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1).strip() + "…";
    }
}
