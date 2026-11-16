import { FiAlertCircle, FiRefreshCw } from "react-icons/fi";
import { AVATAR_STATES } from "../../services/simliService";

/**
 * The interviewer tile of a voice interview: the Simli lip-synced video when the avatar is live,
 * otherwise the built-in CareerAI circle. The status label always comes from the Vapi call
 * (voiceState / stateLabel); the avatar state only decides what is shown.
 *
 * The <video> and <audio> elements are always rendered so the SDK can attach to them. The
 * <audio> element receives Simli's copy of the voice and is kept silent by the avatar service:
 * the candidate hears Vapi's playback only.
 * children (the candidate's self-view) are shown picture-in-picture inside the frame.
 */
function SimliInterviewerAvatar({ voiceState, stateLabel, speaking, avatar, videoRef, audioRef, onRetry, children }) {
  const videoVisible = avatar.status === AVATAR_STATES.READY || avatar.status === AVATAR_STATES.SPEAKING;
  const tileState = speaking ? "speaking" : "listening";

  return (
    <div className={`ai-flow-interviewer-tile simli-avatar-tile ${tileState}${videoVisible ? " has-video" : ""}`} data-voice-state={voiceState} data-avatar-state={avatar.status}>

      <div className="simli-avatar-frame">
        <video
          ref={videoRef}
          className={`simli-avatar-video${videoVisible ? " visible" : ""}`}
          autoPlay
          playsInline
          muted
          aria-label="CareerAI interviewer video"
        />
        <audio ref={audioRef} autoPlay muted className="simli-avatar-audio" />

        {!videoVisible && (
          <div className="ai-avatar-wrapper simli-avatar-fallback">
            <div className="ai-avatar-ring"></div>
            <div className="ai-avatar">AI</div>
          </div>
        )}

        {children && <div className="simli-avatar-pip">{children}</div>}
      </div>

      <div className="simli-avatar-caption">
        <h2>CareerAI Interviewer</h2>
        <span className={`ai-flow-ai-state ${tileState}`} aria-live="polite">
          <span></span>
          {stateLabel}
        </span>
      </div>

      {avatar.status === AVATAR_STATES.CONNECTING && (
        <p className="simli-avatar-note">Loading interviewer video…</p>
      )}

      {avatar.status === AVATAR_STATES.ERROR && (
        <p className="simli-avatar-note error" role="status">
          <FiAlertCircle />
          {avatar.error}
          {avatar.canRetry && (
            <button type="button" className="simli-avatar-retry" onClick={onRetry}>
              <FiRefreshCw />
              Retry Avatar
            </button>
          )}
        </p>
      )}

    </div>
  );
}

export default SimliInterviewerAvatar;
