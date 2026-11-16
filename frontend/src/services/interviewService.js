import api from "./api";

/* =====================================================
   TEXT INTERVIEW SERVICE — Spring Boot interview endpoints.
   Questions, evaluations and reports come from the backend (AI when
   available, the CareerAI question bank otherwise). Each item carries
   source "AI" or "FALLBACK".
===================================================== */

const AI_TIMEOUT = 120000;

/**
 * Body for POST /api/interviews from the setup page's config.
 * mode: "TEXT" (default) or "VOICE".
 */
export function buildStartRequest(config, mode = "TEXT") {
  return {
    type: config?.type || "TECHNICAL",
    targetRole: config?.targetRole,
    difficulty: (config?.difficulty || "Intermediate").toUpperCase(),
    resumeId: config?.resumeId || null,
    jobDescription: config?.jobDescription || null,
    questionCount: config?.questionCount || 6,
    focusTopics: Array.isArray(config?.focusTopics) ? config.focusTopics : [],
    mode
  };
}

/** { type, targetRole, difficulty, resumeId?, jobDescription?, questionCount, focusTopics?, mode? } */
export async function startInterview(request) {
  const { data } = await api.post("/interviews", request, { timeout: AI_TIMEOUT });
  return data;
}

export async function getInterview(id) {
  const { data } = await api.get(`/interviews/${id}`);
  return data;
}

/** Evaluation + next question (one AI call on the server). */
export async function submitAnswer(sessionId, questionId, answer) {
  const { data } = await api.post(`/interviews/${sessionId}/answers`, { questionId, answer }, { timeout: AI_TIMEOUT });
  return data;
}

/**
 * Final voice transcript [{ role, text, timestamp }] → { status: COMPLETED | CANCELLED, report, message }.
 * Safe to retry: the backend processes each interview once and returns the stored result.
 */
export async function submitVoiceTranscript(id, { providerCallId, transcript }) {
  const { data } = await api.post(`/interviews/${id}/voice-transcript`,
    { provider: "VAPI", providerCallId: providerCallId || null, transcript }, { timeout: AI_TIMEOUT });
  return data;
}

export async function completeInterview(id) {
  const { data } = await api.post(`/interviews/${id}/complete`, null, { timeout: AI_TIMEOUT });
  return data;
}

export async function getInterviewReport(id) {
  const { data } = await api.get(`/interviews/${id}/report`);
  return data;
}

export async function getInterviewHistory() {
  const { data } = await api.get("/interviews/history");
  return data;
}

export async function getInterviewStats() {
  const { data } = await api.get("/interviews/stats");
  return data;
}
