import { Link } from "react-router-dom";

const MESSAGES = {
  saving: "Saving to your practice history…",
  saved: "Saved to your practice history.",
  failed: "This result could not be saved to your history. Check that you are online and the CareerAI server is running.",
  guest: "Practising as a guest — sign in to save your practice history."
};

/**
 * One line under a practice result explaining whether it reached the backend history.
 * status: null | "saving" | "saved" | "failed" | "guest"
 */
function PracticeSaveStatus({ status }) {
  if (!status || !MESSAGES[status]) return null;

  return (
    <p
      className="ua-footnote"
      role="status"
    >
      {MESSAGES[status]}
      {status === "saved" && (
        <>
          {" "}
          <Link to="/performance">View recent practice</Link>
        </>
      )}
      {status === "guest" && (
        <>
          {" "}
          <Link to="/login">Log in</Link>
        </>
      )}
    </p>
  );
}

export default PracticeSaveStatus;
