/* =====================================================
   USER SETTINGS (frontend demo)

   Stored per user in localStorage.
   TODO backend: GET/PUT /api/users/me/settings
===================================================== */

export const SETTINGS_KEY = "careerai_settings";
export const SETTINGS_EVENT = "careerai:settings-changed";

export const DIFFICULTIES = ["Beginner", "Intermediate", "Advanced"];
export const DAILY_GOALS = [2, 3, 4, 6];

export const DEFAULT_SETTINGS = {
  defaultDifficulty: "Beginner",
  dailyGoal: 4,
  emailReminders: false,
  streakReminders: true,
  interviewReminders: true,
  useHistoryForRecommendations: true,
  reduceMotion: false,
  compactLayout: false
};

function readAll() {
  try {
    const parsed = JSON.parse(localStorage.getItem(SETTINGS_KEY) || "{}");
    return parsed && typeof parsed === "object" ? parsed : {};
  } catch {
    return {};
  }
}

export function getSettings(userId) {
  const saved = userId ? readAll()[userId] : null;
  const merged = { ...DEFAULT_SETTINGS, ...(saved || {}) };

  // Guard against stale or hand-edited values.
  if (!DIFFICULTIES.includes(merged.defaultDifficulty)) merged.defaultDifficulty = DEFAULT_SETTINGS.defaultDifficulty;
  if (!DAILY_GOALS.includes(Number(merged.dailyGoal))) merged.dailyGoal = DEFAULT_SETTINGS.dailyGoal;
  merged.dailyGoal = Number(merged.dailyGoal);

  return merged;
}

export function saveSettings(userId, settings) {
  if (!userId) return getSettings(null);

  const all = readAll();
  all[userId] = { ...getSettings(userId), ...settings };

  try {
    localStorage.setItem(SETTINGS_KEY, JSON.stringify(all));
  } catch {
    // Storage unavailable — settings apply only until reload.
  }

  window.dispatchEvent(new CustomEvent(SETTINGS_EVENT, { detail: all[userId] }));

  return all[userId];
}

export function clearSettings(userId) {
  const all = readAll();
  delete all[userId];

  try {
    localStorage.setItem(SETTINGS_KEY, JSON.stringify(all));
  } catch {
    // ignore
  }

  window.dispatchEvent(new CustomEvent(SETTINGS_EVENT, { detail: getSettings(userId) }));
}
