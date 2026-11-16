import { useEffect, useRef } from "react";
import { FiMic, FiMicOff, FiUser, FiVideo, FiVideoOff } from "react-icons/fi";
import { CAMERA_STATUS, useCameraState } from "../../services/candidateCamera";

/**
 * Local, muted, mirrored self-view of a camera stream. Display only: the stream is never
 * recorded, uploaded or sent anywhere.
 */
export function CameraPreview({ stream, label = "Your camera preview", className = "" }) {
  const videoRef = useRef(null);

  useEffect(() => {
    const video = videoRef.current;
    if (!video) return undefined;
    video.srcObject = stream || null;
    return () => {
      if (video.srcObject === stream) video.srcObject = null;
    };
  }, [stream]);

  return (
    <video
      ref={videoRef}
      className={`candidate-camera-video ${className}`.trim()}
      autoPlay
      playsInline
      muted
      aria-label={label}
    />
  );
}

function initialsOf(name) {
  return String(name || "")
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join("");
}

/**
 * The candidate's picture-in-picture tile in the voice interview. The camera stream is owned
 * by a CandidateCameraController (opened/stopped there); the microphone state shown here comes
 * from the Vapi call and is only displayed.
 */
function CandidateCamera({ controller, name, muted = false, speaking = false }) {
  const camera = useCameraState(controller);
  const on = camera.status === CAMERA_STATUS.ON && Boolean(camera.stream);
  const statusText = on ? "Camera On" : camera.status === CAMERA_STATUS.STARTING ? "Starting camera…" : "Camera Off";
  const initials = initialsOf(name);

  return (
    <div className={`candidate-camera ${on ? "on" : "off"}${speaking ? " speaking" : ""}`} data-camera-state={camera.status}>
      {on ? (
        <CameraPreview stream={camera.stream} />
      ) : (
        <div className="candidate-camera-placeholder" aria-hidden="true">
          {initials || <FiUser />}
        </div>
      )}
      <div className="candidate-camera-caption">
        <strong>You</strong>
        <span>
          {on ? <FiVideo aria-hidden="true" /> : <FiVideoOff aria-hidden="true" />}
          {statusText}
        </span>
        <span className="candidate-camera-mic">
          {muted ? <FiMicOff aria-hidden="true" /> : <FiMic aria-hidden="true" />}
          {muted ? "Muted" : speaking ? "Speaking" : "Mic on"}
        </span>
      </div>
    </div>
  );
}

export default CandidateCamera;
