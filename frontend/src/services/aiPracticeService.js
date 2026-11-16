import api from "./api";

/* =====================================================
   AI PRACTICE SERVICE — Spring Boot AI endpoints.
   The browser never calls Gemini and never sees an API key;
   the backend builds prompts, validates the AI JSON and returns DTOs.
===================================================== */

// Generation can take a while (backend read timeout is 90 s); the default 15 s API timeout is too short.
const AI_TIMEOUT = 120000;

/** { available, provider } — false when the server has no AI key configured. */
export async function getAiStatus() {
  const { data } = await api.get("/ai/practice/status");
  return data;
}

/** { module, topic, difficulty, questionType: "MCQ" | "DESCRIPTIVE", count } */
export async function generateAiQuestions(request) {
  const { data } = await api.post("/ai/practice/questions", request, { timeout: AI_TIMEOUT });
  return data;
}

/** { module, topic, difficulty, question, answer } → { score, strengths, missingPoints, feedback, suggestedAnswer } */
export async function evaluateAiAnswer(request) {
  const { data } = await api.post("/ai/practice/evaluate", request, { timeout: AI_TIMEOUT });
  return data;
}

/** { topic, difficulty, sections: { requirements, api, ... } } → rubric review */
export async function evaluateAiSystemDesign(request) {
  const { data } = await api.post("/ai/practice/system-design/evaluate", request, { timeout: AI_TIMEOUT });
  return data;
}
