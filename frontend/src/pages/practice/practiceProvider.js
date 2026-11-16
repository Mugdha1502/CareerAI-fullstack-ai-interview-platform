// Practice content provider.
//
// Every workspace asks this module for content using the same request shape
// the CareerAI backend accepts:
//   { module, topic, difficulty, questionType, count }
//
// Technical and aptitude questions, descriptive evaluation and system design
// review come from the Spring Boot AI endpoints when the user is signed in and
// the AI service is available; otherwise the mock content below is used and the
// result is labelled as a fallback. Returned shapes are the same either way.
// Never call AI providers directly from React or ship API keys to the browser.
//
// Coding and SQL execution stay mock: AI never decides whether code or a query ran.

import { getToken, toApiError } from "../../services/api";
import {
  evaluateAiAnswer,
  evaluateAiSystemDesign,
  generateAiQuestions
} from "../../services/aiPracticeService";
import { evaluateDescriptiveAnswer, reviewSystemDesign } from "./practiceEvaluators";
import {
  technicalMcqs,
  technicalNotes,
  technicalDescriptive
} from "./technicalMockData";
import {
  getAptitudeSource,
  codingProblems,
  sqlDatasets,
  sqlProblems,
  systemDesignChallenges,
  voiceTopics,
  dailyPools
} from "./practiceMockData";

function shuffle(list) {
  const copy = [...list];

  for (let i = copy.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1));
    [copy[i], copy[j]] = [copy[j], copy[i]];
  }

  return copy;
}

let questionCounter = 0;

// Converts mock/API MCQs into the shape the quiz UI expects.
export function normalizeMcq(raw, category = "Technical") {
  const options = shuffle(raw.options);

  questionCounter += 1;

  return {
    id: `q-${questionCounter}`,
    question: raw.question,
    options,
    answerIndex: options.indexOf(raw.answer),
    explanation: raw.explanation,
    tag: raw.tag || category,
    table: raw.table || null,
    category
  };
}

function aptitudeQuestions(topic, difficulty, count) {
  const source = getAptitudeSource(topic);

  if (source.type === "generated") {
    const questions = [];
    const seen = new Set();
    let attempts = 0;

    // Avoid identical generated questions within one session.
    while (questions.length < count && attempts < count * 20) {
      const raw = source.generate(difficulty);
      attempts += 1;

      if (!seen.has(raw.question)) {
        seen.add(raw.question);
        questions.push(normalizeMcq(raw, "Aptitude"));
      }
    }

    return questions;
  }

  return shuffle(source.questions)
    .slice(0, count)
    .map((raw) => normalizeMcq(raw, "Aptitude"));
}

/**
 * request: { module, topic, difficulty, questionType, count }
 * TODO backend: POST /api/practice/questions with this request body.
 */
export function getPracticeQuestions({ module, topic, difficulty, questionType, count = 10 }) {
  if (module === "technical" && questionType === "mcq") {
    return shuffle(technicalMcqs[topic] || [])
      .slice(0, count)
      .map((raw) => normalizeMcq(raw, "Technical"));
  }

  if (module === "technical" && questionType === "descriptive") {
    return (technicalDescriptive[topic] || []).slice(0, count);
  }

  if (module === "aptitude") {
    return aptitudeQuestions(topic, difficulty, count);
  }

  return [];
}

/* =====================================================
   AI-FIRST LOADING WITH MOCK FALLBACK
   Each result carries { source, notice } so the UI can label it:
     source "ai"       → generated/evaluated by the CareerAI AI service
     source "fallback" → AI unavailable or failed; CareerAI demo content used
     source "demo"     → not signed in, so AI was not attempted
===================================================== */

const AI_MODULES = ["technical", "aptitude"];

const GUEST_NOTICE = "Sign in to get AI-generated practice. Showing CareerAI demo content.";

// Short reasons for the backend's AI error codes (see GlobalExceptionHandler).
const FALLBACK_REASONS = {
  AI_UNAVAILABLE: "AI is not configured on the server",
  AI_TIMEOUT: "the AI service timed out",
  AI_BUSY: "the AI service is busy",
  AI_RATE_LIMITED: "the hourly AI limit was reached",
  AI_INVALID_RESPONSE: "the AI answer failed quality checks",
  AI_BLOCKED: "the AI service declined the request",
  AI_CONFIGURATION: "the AI service is misconfigured"
};

function fallbackNotice(error, what) {
  const { status } = toApiError(error);
  const code = error?.response?.data?.code;
  const reason = status === 0
    ? "the CareerAI server could not be reached"
    : FALLBACK_REASONS[code] || "the AI request could not be completed";

  return `AI ${what} unavailable: ${reason}. Showing CareerAI demo content instead.`;
}

function keywordsFromLabel(label) {
  return label
    .toLowerCase()
    .split(/[^a-z0-9+#.]+/)
    .filter((word) => word.length >= 4);
}

/** AI DTO → the shapes the workspaces already use. */
function fromAiQuestion(item, module, questionType, topic) {
  if (questionType === "descriptive") {
    return {
      question: item.question,
      keyPoints: item.expectedKeyPoints.map((label) => ({ label, keywords: keywordsFromLabel(label) })),
      strongerAnswer: item.referenceAnswer,
      source: "ai"
    };
  }

  return normalizeMcq(
    { question: item.question, options: item.options, answer: item.correctAnswer, explanation: item.explanation, tag: topic },
    module === "aptitude" ? "Aptitude" : "Technical"
  );
}

/**
 * request: { module, topic, difficulty, questionType: "mcq" | "descriptive", count }
 * Resolves to { questions, source, notice }. Never rejects: failures fall back to mock content.
 */
export async function loadPracticeQuestions(request) {
  const mock = () => getPracticeQuestions(request);

  if (!AI_MODULES.includes(request.module)) {
    return { questions: mock(), source: "demo", notice: null };
  }

  if (!getToken()) {
    return { questions: mock(), source: "demo", notice: GUEST_NOTICE };
  }

  try {
    const data = await generateAiQuestions({
      module: request.module,
      topic: request.topic,
      difficulty: request.difficulty,
      questionType: request.questionType === "descriptive" ? "DESCRIPTIVE" : "MCQ",
      count: request.count
    });

    return {
      questions: data.questions.map((item) => fromAiQuestion(item, request.module, request.questionType, data.topic)),
      source: "ai",
      notice: null
    };
  } catch (error) {
    return { questions: mock(), source: "fallback", notice: fallbackNotice(error, "questions are") };
  }
}

/**
 * Evaluates one descriptive answer. Resolves to a common shape:
 * { source, notice, percent, scoreText, covered[], missing[], feedbackText, strongerAnswer, lengthNote }
 */
export async function evaluatePracticeAnswer({ module, topic, difficulty }, prompt, answer) {
  const demo = (source, notice) => {
    const result = evaluateDescriptiveAnswer(prompt, answer);
    return {
      source,
      notice,
      percent: result.total ? Math.round((result.score / result.total) * 100) : 0,
      scoreText: `${result.score} / ${result.total} key points covered`,
      covered: result.covered,
      missing: result.missing,
      feedbackText: null,
      strongerAnswer: result.strongerAnswer,
      lengthNote: result.lengthNote
    };
  };

  if (!getToken()) {
    return demo("demo", GUEST_NOTICE);
  }

  try {
    const result = await evaluateAiAnswer({ module, topic, difficulty, question: prompt.question, answer });
    return {
      source: "ai",
      notice: null,
      percent: result.score,
      scoreText: `${result.score} / 100`,
      covered: result.strengths,
      missing: result.missingPoints,
      feedbackText: result.feedback,
      strongerAnswer: result.suggestedAnswer,
      lengthNote: null
    };
  } catch (error) {
    return demo("fallback", fallbackNotice(error, "evaluation is"));
  }
}

/**
 * Reviews a system design. AI result: { source: "ai", overallScore, rubric[{criterion,label,score,feedback}],
 * strengths, recommendations, summary }. Fallback: the demo completeness rubric with source/notice added.
 */
export async function reviewPracticeDesign({ topic, difficulty }, sections, answers) {
  const demo = (source, notice) => ({ ...reviewSystemDesign(sections, answers), source, notice });

  if (!getToken()) {
    return demo("demo", GUEST_NOTICE);
  }

  try {
    const payload = Object.fromEntries(
      sections.map((section) => [section.id, (answers[section.id] || "").trim()])
    );
    const result = await evaluateAiSystemDesign({ topic, difficulty, sections: payload });
    return { ...result, source: "ai", notice: null };
  } catch (error) {
    const { status, message } = toApiError(error);
    // A 400 (e.g. design too short) is the user's to fix, not an AI outage.
    if (status === 400) {
      return { source: "invalid", notice: message };
    }
    return demo("fallback", fallbackNotice(error, "design review is"));
  }
}

// TODO backend: GET /api/practice/notes?topic=Java (content from DB or AI).
export function getStudyNotes(topic) {
  return technicalNotes[topic] || [];
}

// TODO backend: GET /api/practice/coding/problems?topic=...&difficulty=...
export function getCodingProblem(topic) {
  return codingProblems[topic];
}

// TODO backend: GET /api/practice/sql/problems — runs against an isolated
// practice database, never the CareerAI application database.
export function getSqlProblem(dataset, topic) {
  return {
    dataset: sqlDatasets[dataset],
    problem: sqlProblems[dataset]?.[topic]
  };
}

export function getSystemDesignChallenge(topic) {
  return systemDesignChallenges[topic];
}

export function getVoicePrompts(topic) {
  return voiceTopics[topic];
}

/* =====================================================
   DAILY CHALLENGE
   Stable for the whole calendar day: the same date always yields the
   same four questions.
   TODO backend: GET /api/practice/daily-challenge
===================================================== */

export function getTodayKey(date = new Date()) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");

  return `${year}-${month}-${day}`;
}

export function getDailyChallenge(date = new Date()) {
  const dayNumber = Math.floor(
    new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime() / 86400000
  );

  const categories = ["Technical", "Aptitude", "SQL", "Coding"];

  const questions = categories.map((category, index) => {
    const pool = dailyPools[category];
    const raw = pool[(dayNumber + index) % pool.length];

    // Options are not shuffled so the challenge looks identical all day.
    return {
      id: `daily-${category}`,
      question: raw.question,
      options: raw.options,
      answerIndex: raw.options.indexOf(raw.answer),
      explanation: raw.explanation,
      tag: raw.tag,
      table: null,
      category
    };
  });

  const difficulties = ["Beginner", "Intermediate", "Advanced"];

  return {
    dateKey: getTodayKey(date),
    difficulty: difficulties[dayNumber % 3],
    estimatedMinutes: 8,
    questions
  };
}

/* =====================================================
   MOCK ASSESSMENT
   TODO backend: POST /api/practice/assessments with
   { assessmentType, difficulty, count, durationMinutes }.
===================================================== */

const assessmentSources = {
  "Java Assessment": ["Java", "Spring Boot"],
  "Frontend Assessment": ["JavaScript", "React"],
  "Backend Assessment": ["Spring Boot", "REST APIs", "DBMS", "Java"],
  "SQL Assessment": ["DBMS"],
  "Aptitude Test": [],
  "Mixed Assessment": ["Java", "DBMS", "REST APIs", "JavaScript"]
};

const aptitudeMix = ["Percentages", "Profit & Loss", "Ratio", "Time & Work", "Logical Reasoning", "Data Interpretation"];

export function buildAssessment({ assessmentType, difficulty, count }) {
  const technicalTopics = assessmentSources[assessmentType] || [];
  const includeAptitude = ["Aptitude Test", "Mixed Assessment"].includes(assessmentType);

  // SQL assessment draws extra SQL questions from the daily pool.
  const technicalRaw = [
    ...technicalTopics.flatMap((topic) => technicalMcqs[topic] || []),
    ...(assessmentType === "SQL Assessment" ? dailyPools.SQL : [])
  ];

  const technicalTarget = includeAptitude
    ? assessmentType === "Aptitude Test" ? 0 : Math.ceil(count / 2)
    : count;

  let technical = shuffle(technicalRaw)
    .slice(0, technicalTarget)
    .map((raw) => normalizeMcq(raw, "Technical"));

  // Top up from the wider technical bank if the chosen area is too small.
  if (technical.length < technicalTarget) {
    const extra = shuffle(Object.values(technicalMcqs).flat().filter((raw) => !technicalRaw.includes(raw)))
      .slice(0, technicalTarget - technical.length)
      .map((raw) => normalizeMcq(raw, "Technical"));

    technical = [...technical, ...extra];
  }

  const aptitudeNeeded = count - technical.length;
  const aptitude = [];

  for (let i = 0; aptitude.length < aptitudeNeeded && i < aptitudeNeeded * 3; i += 1) {
    const topic = aptitudeMix[i % aptitudeMix.length];
    const [question] = aptitudeQuestions(topic, difficulty, 1);

    if (question && !aptitude.some((item) => item.question === question.question)) {
      aptitude.push(question);
    }
  }

  return shuffle([...technical, ...aptitude]).slice(0, count);
}
