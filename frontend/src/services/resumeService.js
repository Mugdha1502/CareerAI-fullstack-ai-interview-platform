import api from "./api";

/* =====================================================
   RESUME SERVICE — resumes, versions and rule-based analysis.
   All calls use the shared Axios instance (JWT attached there);
   the backend resolves the user from the token, never from these calls.
===================================================== */

// Must match the backend limits (app.storage.resume-max-bytes, JobMatchService).
export const MAX_RESUME_BYTES = 5 * 1024 * 1024;
export const MIN_JOB_DESCRIPTION_LENGTH = 80;
export const ACCEPTED_RESUME_TYPES = ".pdf,.doc,.docx";

const UPLOAD_TIMEOUT = 60000;

// ATS, job match, improvement and interview preparation may call the AI service
// (backend read timeout 90 s plus retries); they fall back to rules on the server.
const AI_TIMEOUT = 120000;

/** Client-side pre-check so obvious mistakes fail fast; the backend validates again. */
export function validateResumeFile(file) {
  if (!file) return "Please choose a resume file.";
  const extension = file.name.split(".").pop()?.toLowerCase();
  if (!["pdf", "doc", "docx"].includes(extension)) {
    return "Unsupported file type. Please upload a PDF, DOC or DOCX resume.";
  }
  if (file.size > MAX_RESUME_BYTES) {
    return "Resume file is too large. Maximum size is 5 MB.";
  }
  return "";
}


/* ---------- Resumes ---------- */

export async function uploadResume(file, title) {
  const form = new FormData();
  form.append("file", file);
  if (title) form.append("title", title);

  const { data } = await api.post("/resumes/upload", form, {
    headers: { "Content-Type": "multipart/form-data" },
    timeout: UPLOAD_TIMEOUT
  });
  return data;
}

/** Creates a Resume Builder resume: { title, content }. */
export async function createResume(data) {
  const { data: resume } = await api.post("/resumes", data);
  return resume;
}

export async function getResumes() {
  const { data } = await api.get("/resumes");
  return data;
}

export async function getResume(id) {
  const { data } = await api.get(`/resumes/${id}`);
  return data;
}

/** { title?, content? } — content only for builder resumes. */
export async function updateResume(id, data) {
  const { data: resume } = await api.put(`/resumes/${id}`, data);
  return resume;
}

export async function deleteResume(id) {
  await api.delete(`/resumes/${id}`);
}

export async function setPrimaryResume(id) {
  const { data } = await api.put(`/resumes/${id}/primary`);
  return data;
}


/* ---------- Versions ---------- */

export async function getVersions(resumeId) {
  const { data } = await api.get(`/resumes/${resumeId}/versions`);
  return data;
}

/** Snapshots the resume's current content; versionName is optional. */
export async function createVersion(resumeId, data = {}) {
  const { data: version } = await api.post(`/resumes/${resumeId}/versions`, data);
  return version;
}

export async function renameVersion(resumeId, versionId, versionName) {
  const { data } = await api.put(`/resumes/${resumeId}/versions/${versionId}`, { versionName });
  return data;
}

export async function duplicateVersion(resumeId, versionId) {
  const { data } = await api.post(`/resumes/${resumeId}/versions/${versionId}/duplicate`);
  return data;
}

export async function deleteVersion(resumeId, versionId) {
  await api.delete(`/resumes/${resumeId}/versions/${versionId}`);
}


/* ---------- Rule-based analysis ---------- */

export async function analyzeATS(resumeId) {
  const { data } = await api.post(`/resumes/${resumeId}/ats-analysis`, null, { timeout: AI_TIMEOUT });
  return data;
}

/** Latest stored ATS analysis, or null when the resume has not been analysed (204). */
export async function getLatestATSAnalysis(resumeId) {
  const response = await api.get(`/resumes/${resumeId}/ats-analysis/latest`);
  return response.status === 204 || !response.data ? null : response.data;
}

/** { jobTitle?, company?, jobDescription } */
export async function analyzeJobMatch(resumeId, data) {
  const { data: match } = await api.post(`/resumes/${resumeId}/job-match`, data, { timeout: AI_TIMEOUT });
  return match;
}

export async function getJobMatches() {
  const { data } = await api.get("/resumes/job-matches");
  return data;
}

export async function getJobMatch(matchId) {
  const { data } = await api.get(`/resumes/job-matches/${matchId}`);
  return data;
}

/** { resumeId?, text?, section } — AI-assisted when available, basic wording assistance otherwise. */
export async function improveResume(data) {
  const { data: result } = await api.post("/resumes/improve", data, { timeout: AI_TIMEOUT });
  return result;
}

/** { targetRole?, jobDescription? } → focus topics and extracted context. */
export async function prepareInterview(resumeId, data = {}) {
  const { data: context } = await api.post(`/resumes/${resumeId}/interview-preparation`, data, { timeout: AI_TIMEOUT });
  return context;
}
