/* =====================================================
   AI COACH — page helpers.
   Conversations now live on the backend (see services/coachService.js);
   this file only keeps the suggested prompts and the cleanup of the old
   browser-only history used by Settings → Clear local data.
===================================================== */

export const SUGGESTED_PROMPTS = [
  "What should I practice today?",
  "Explain my Skill Gap.",
  "Am I ready for a full-stack interview?",
  "What should I focus on this week?",
  "Create a 60-minute preparation plan for today."
];

// Key used by the earlier demo Coach, which stored conversations in this browser.
const LEGACY_CONVERSATIONS_KEY = "careerai_coach_conversations";

/** Removes the old browser-only Coach history for a user (server conversations are unaffected). */
export function clearConversations(userId) {
  try {
    const all = JSON.parse(localStorage.getItem(LEGACY_CONVERSATIONS_KEY) || "{}") || {};
    delete all[userId];
    localStorage.setItem(LEGACY_CONVERSATIONS_KEY, JSON.stringify(all));
  } catch {
    // ignore
  }
}
