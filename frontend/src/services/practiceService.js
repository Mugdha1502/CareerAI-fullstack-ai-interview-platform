import api, { getToken } from "./api";

/* =====================================================
   PRACTICE SERVICE
   POST /practice/sessions                start a session
   PUT  /practice/sessions/{id}/complete  complete without a result
   POST /practice/results                 save result (also completes the session)
   GET  /practice/history                 paged history
   GET  /practice/recent                  last 5 results

   Questions are still generated on the frontend; only sessions and
   results are stored. The user always comes from the JWT.
===================================================== */

/** Practice is only saved for signed-in users (a stored JWT). Guests practise locally. */
export function canSavePractice() {
  return Boolean(getToken());
}

export async function startPracticeSession({ module, topic, difficulty, questionType }) {
  const { data } = await api.post("/practice/sessions", {
    module,
    topic: topic || null,
    difficulty: difficulty || null,
    questionType: questionType || null
  });
  return data;
}

export async function completePracticeSession(sessionId) {
  const { data } = await api.put(`/practice/sessions/${sessionId}/complete`);
  return data;
}

/** Correct answers default to the score; incorrect = total - correct. */
export async function savePracticeResult({ sessionId, score, totalQuestions, correctAnswers }) {
  const total = Math.max(Number(totalQuestions) || 0, 1);
  const safeScore = Math.min(Math.max(Number(score) || 0, 0), total);
  const correct = Math.min(Math.max(Number(correctAnswers ?? safeScore) || 0, 0), total);

  const { data } = await api.post("/practice/results", {
    sessionId,
    score: safeScore,
    totalQuestions: total,
    correctAnswers: correct,
    incorrectAnswers: total - correct
  });
  return data;
}

export async function getPracticeHistory({ module, page = 0, size = 20 } = {}) {
  const { data } = await api.get("/practice/history", {
    params: { page, size, ...(module ? { module } : {}) }
  });
  return data;
}

export async function getRecentPractice() {
  const { data } = await api.get("/practice/recent");
  return data;
}


/* =====================================================
   CODING + SQL EXECUTION (server-side only)
   GET  /practice/coding/question?topic=   curated question (no hidden tests)
   POST /practice/coding/execute           Run Code: custom stdin, never saved
   POST /practice/coding/submit            Submit: hidden tests, saved as a result
   GET  /practice/sql/question?dataset=&topic=
   POST /practice/sql/execute              Run Query in a disposable sandbox, never saved
   POST /practice/sql/submit               Submit: result compared on visible + hidden data, saved

   Code runs only in the backend's external sandbox (JDoodle by default; Judge0 optional) —
   provider credentials never reach the browser. SQL runs only in a disposable
   in-memory database. Limits and language ids are decided by the server.
===================================================== */

/** UI language label → API language name. */
export const CODE_LANGUAGE_IDS = {
  Java: "JAVA",
  Python: "PYTHON",
  JavaScript: "JAVASCRIPT",
  "C++": "CPP"
};

export const EXECUTION_UNAVAILABLE_MESSAGE = "Code execution is temporarily unavailable";

export async function getCodingQuestion(topic) {
  const { data } = await api.get("/practice/coding/question", { params: { topic } });
  return data;
}

export async function executeCode({ language, sourceCode, stdin, questionId }) {
  const { data } = await api.post("/practice/coding/execute", { language, sourceCode, stdin, questionId }, { timeout: 30000 });
  return data;
}

export async function submitCode({ questionId, language, sourceCode, sessionId }) {
  const { data } = await api.post("/practice/coding/submit", { questionId, language, sourceCode, sessionId: sessionId || null }, { timeout: 45000 });
  return data;
}

export async function getSqlQuestion({ dataset, topic }) {
  const { data } = await api.get("/practice/sql/question", { params: { dataset, topic } });
  return data;
}

export async function executeSql({ questionId, query }) {
  const { data } = await api.post("/practice/sql/execute", { questionId, query });
  return data;
}

export async function submitSql({ questionId, query, sessionId }) {
  const { data } = await api.post("/practice/sql/submit", { questionId, query, sessionId: sessionId || null });
  return data;
}

/** { status, code, message } for execution errors; never raw server internals. */
export function executionError(error) {
  const status = error?.response?.status || 0;
  const code = error?.response?.data?.code || null;
  if (!error?.response) {
    return { status, code: "NETWORK", message: "Unable to reach the CareerAI server. Check that the backend is running." };
  }
  if (code === "EXECUTION_PROVIDER_UNAVAILABLE") {
    return { status, code, message: EXECUTION_UNAVAILABLE_MESSAGE };
  }
  if (code === "EXECUTION_PROVIDER_LIMIT_REACHED") {
    return { status, code, message: "Code execution provider daily limit reached. Please try again later." };
  }
  if (code === "EXECUTION_TIMEOUT") {
    return { status, code, message: "Execution timed out. Please try again." };
  }
  const message = error.response.data?.message || "The request could not be completed. Please try again.";
  return { status, code, message };
}
