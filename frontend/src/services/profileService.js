import api from "./api";

/* =====================================================
   PROFILE SERVICE
   GET  /profile/me          current user's profile
   PUT  /profile/me          replace profile fields
   POST /profile/onboarding  save profile + mark onboarding complete

   The rest of the app reads user.profile in this shape:
     { status, education, experience, skills: "Java, SQL",
       targetRole, goal, dailyTime: "60", interviewDifficulty }
   so responses are mapped to it, and requests are mapped back to the API names.
===================================================== */

export function splitSkills(text) {
  return String(text || "")
    .split(",")
    .map((skill) => skill.trim())
    .filter(Boolean);
}

/** App profile → API request body. */
export function toApiProfile(profile = {}) {
  const minutes = Number.parseInt(profile.dailyTime, 10);

  return {
    currentRole: profile.status || "",
    education: profile.education || "",
    experienceLevel: profile.experience || "",
    skills: Array.isArray(profile.skills) ? profile.skills : splitSkills(profile.skills),
    targetRole: profile.targetRole || "",
    careerGoal: profile.goal || "",
    dailyPreparationMinutes: Number.isFinite(minutes) ? minutes : null,
    preferredDifficulty: profile.interviewDifficulty || ""
  };
}

/** API response → app profile, or null when nothing has been saved yet. */
export function fromApiProfile(data) {
  if (!data) return null;

  const profile = {
    status: data.currentRole || "",
    education: data.education || "",
    experience: data.experienceLevel || "",
    skills: (data.skills || []).join(", "),
    targetRole: data.targetRole || "",
    goal: data.careerGoal || "",
    dailyTime: data.dailyPreparationMinutes != null ? String(data.dailyPreparationMinutes) : "",
    interviewDifficulty: data.preferredDifficulty || ""
  };

  const hasData = Object.values(profile).some(Boolean);
  return hasData ? profile : null;
}

export async function getProfile() {
  const { data } = await api.get("/profile/me");
  return { profile: fromApiProfile(data), onboardingCompleted: Boolean(data.onboardingCompleted) };
}

export async function updateProfile(profile) {
  const { data } = await api.put("/profile/me", toApiProfile(profile));
  return { profile: fromApiProfile(data), onboardingCompleted: Boolean(data.onboardingCompleted) };
}

export async function completeOnboarding(profile) {
  const { data } = await api.post("/profile/onboarding", toApiProfile(profile));
  return { profile: fromApiProfile(data), onboardingCompleted: Boolean(data.onboardingCompleted) };
}
