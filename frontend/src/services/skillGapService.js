import api from "./api";

/* =====================================================
   SKILL GAP SERVICE
   GET /skill-gap            → gap against the profile's target role
   GET /skill-gap?trackId=   → preview another career track (same user)
===================================================== */

export async function getSkillGap(trackId) {
  const { data } = await api.get("/skill-gap", { params: trackId ? { trackId } : {} });
  return data;
}
