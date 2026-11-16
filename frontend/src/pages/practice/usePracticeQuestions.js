import { useEffect, useRef, useState } from "react";
import { loadPracticeQuestions } from "./practiceProvider";

/**
 * Loads one practice set (AI first, mock fallback) once per mounted workspace.
 * The request promise is kept in a ref so React StrictMode's double effect in
 * development does not send a second AI request.
 * Returns { status: "loading" | "ready", questions, source, notice }.
 */
export function usePracticeQuestions(request) {
  const [state, setState] = useState({ status: "loading", questions: [], source: null, notice: null });
  const pending = useRef(null);

  useEffect(() => {
    let active = true;

    if (!pending.current) {
      pending.current = loadPracticeQuestions(request);
    }

    pending.current.then((result) => {
      if (active) setState({ status: "ready", ...result });
    });

    return () => {
      active = false;
    };
    // The request is fixed for the lifetime of a workspace (a new config remounts it).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return state;
}
