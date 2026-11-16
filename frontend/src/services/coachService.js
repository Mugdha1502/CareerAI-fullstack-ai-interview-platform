import api from "./api";

/* =====================================================
   AI COACH SERVICE — persistent conversations on the backend.
   The backend builds CareerAI context and calls the AI provider; no AI key or
   provider call ever happens in the browser. Replies have source "AI" or
   "FALLBACK" (deterministic advice when the AI is unavailable).
===================================================== */

// One AI call per message; allow for provider latency plus retries.
const COACH_TIMEOUT = 120000;

export async function getConversations() {
  const { data } = await api.get("/coach/conversations");
  return data;
}

export async function createConversation(title) {
  const { data } = await api.post("/coach/conversations", title ? { title } : {});
  return data;
}

/** { conversation, messages[] } */
export async function getConversation(id) {
  const { data } = await api.get(`/coach/conversations/${id}`);
  return data;
}

export async function deleteConversation(id) {
  await api.delete(`/coach/conversations/${id}`);
}

/** { conversation, userMessage, reply, message, suggestedActions, usedPersonalization, source, notice } */
export async function sendCoachMessage(conversationId, message) {
  const { data } = await api.post(`/coach/conversations/${conversationId}/messages`, { message }, { timeout: COACH_TIMEOUT });
  return data;
}
