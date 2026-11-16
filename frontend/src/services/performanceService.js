import api from "./api";

/* =====================================================
   PERFORMANCE SERVICE — calculated by the backend from saved practice results.
   GET /performance/summary | /history | /skills | /recent-activity
===================================================== */

export async function getPerformanceSummary() {
  const { data } = await api.get("/performance/summary");
  return data;
}

/** Daily average score, oldest first (for the readiness trend line chart). */
export async function getPerformanceHistory(days = 30) {
  const { data } = await api.get("/performance/history", { params: { days } });
  return data;
}

export async function getSkillPerformance() {
  const { data } = await api.get("/performance/skills");
  return data;
}

export async function getRecentActivity() {
  const { data } = await api.get("/performance/recent-activity");
  return data;
}
