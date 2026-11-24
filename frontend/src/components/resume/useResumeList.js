import { useCallback, useEffect, useState } from "react";
import { toApiError } from "../../services/api";
import { getResumes } from "../../services/resumeService";
import { useAuth } from "../../context/auth";

/**
 * Loads the signed-in user's resumes for the resume tool pages.
 * status: "loading" | "ready" | "error" | "guest" (guests have no backend account).
 */
export function useResumeList() {
  const { isAuthenticated } = useAuth();
  const [resumes, setResumes] = useState([]);
  const [status, setStatus] = useState(isAuthenticated ? "loading" : "guest");
  const [error, setError] = useState("");

  useEffect(() => {
    if (!isAuthenticated) return undefined;

    let cancelled = false;

    getResumes()
      .then((list) => {
        if (cancelled) return;
        setResumes(list);
        setStatus("ready");
      })
      .catch((err) => {
        if (cancelled) return;
        setError(toApiError(err).message);
        setStatus("error");
      });

    return () => {
      cancelled = true;
    };
  }, [isAuthenticated]);

  /** Retry after an error (called from event handlers). */
  const reload = useCallback(async () => {
    if (!isAuthenticated) return [];

    setStatus("loading");
    setError("");

    try {
      const list = await getResumes();
      setResumes(list);
      setStatus("ready");
      return list;
    } catch (err) {
      setError(toApiError(err).message);
      setStatus("error");
      return [];
    }
  }, [isAuthenticated]);

  /** Adds or replaces one resume in the local list (e.g. after an upload). */
  const upsert = useCallback((resume) => {
    setResumes((current) => [resume, ...current.filter((item) => item.id !== resume.id)]);
  }, []);

  return { resumes, status, error, reload, upsert };
}

/** Primary resume first, otherwise the most recently updated one. */
export function pickDefaultResume(resumes) {
  return resumes.find((resume) => resume.primary) || resumes[0] || null;
}

export function describeResume(resume) {
  const kind = resume.sourceType === "BUILDER" ? "Builder" : resume.fileType;
  const parts = [kind, `${resume.wordCount} words`];
  if (resume.primary) parts.push("Primary");
  return parts.join(" · ");
}

export function formatDateTime(value) {
  if (!value) return "";
  return new Date(value).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}
