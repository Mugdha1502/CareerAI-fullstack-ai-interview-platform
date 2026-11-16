import { useEffect, useRef, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import {
  FiMic,
  FiVolume2,
  FiVideo,
  FiMonitor,
  FiCheckCircle,
  FiInfo,
  FiLoader,
  FiRefreshCw,
  FiArrowLeft,
  FiArrowRight,
  FiAlertCircle,
  FiType,
  FiVideoOff
} from "react-icons/fi";
import { CameraPreview } from "../../components/interview/CandidateCamera";
import { toApiError } from "../../services/api";
import { CAMERA_MESSAGES, CAMERA_STATUS, listCameras, stopTracks } from "../../services/candidateCamera";
import { MIC_STATUS, checkDevices } from "../../services/deviceCheck";
import { buildStartRequest, startInterview } from "../../services/interviewService";
import { isVoiceAvailable } from "../../services/vapiService";

const DEFAULT_CONFIG = {
  source: "Role-Based",
  category: "Technical",
  targetRole: "Java Full Stack Developer",
  difficulty: "Intermediate",
  duration: "20 minutes",
  mode: "VOICE"
};

const MIC_TEXT = {
  [MIC_STATUS.READY]: { status: "Microphone Ready", tone: "ready", detail: "Microphone access granted." },
  [MIC_STATUS.DENIED]: { status: "Permission Denied", tone: "error", detail: "Allow microphone access in your browser's site settings, then retry." },
  [MIC_STATUS.NOT_FOUND]: { status: "No Microphone Found", tone: "error", detail: "Connect a microphone or headset, then retry." },
  [MIC_STATUS.UNSUPPORTED]: { status: "Browser Unsupported", tone: "error", detail: "This browser cannot access a microphone. Use a recent Chrome, Edge or Firefox over HTTPS." },
  [MIC_STATUS.ERROR]: { status: "Microphone Error", tone: "error", detail: "The microphone could not be opened. Close other apps using it, then retry." }
};

const CAMERA_TEXT = {
  [CAMERA_STATUS.ON]: { status: "Camera Ready", tone: "ready", detail: "Camera access granted. Check your framing in the preview." },
  [CAMERA_STATUS.DENIED]: { status: "Permission Denied", tone: "warning", detail: CAMERA_MESSAGES[CAMERA_STATUS.DENIED] },
  [CAMERA_STATUS.NOT_FOUND]: { status: "No Camera Found", tone: "warning", detail: CAMERA_MESSAGES[CAMERA_STATUS.NOT_FOUND] },
  [CAMERA_STATUS.BUSY]: { status: "Camera Busy", tone: "warning", detail: `${CAMERA_MESSAGES[CAMERA_STATUS.BUSY]} Close other apps using it, or continue with voice only.` },
  [CAMERA_STATUS.UNSUPPORTED]: { status: "Not Supported", tone: "warning", detail: CAMERA_MESSAGES[CAMERA_STATUS.UNSUPPORTED] },
  [CAMERA_STATUS.ERROR]: { status: "Camera Error", tone: "warning", detail: CAMERA_MESSAGES[CAMERA_STATUS.ERROR] }
};

function DeviceCheck() {
  const navigate = useNavigate();
  const { state } = useLocation();
  const interviewConfig = { ...DEFAULT_CONFIG, ...(state || {}) };
  const voiceConfigured = isVoiceAvailable();

  const [mic, setMic] = useState(null);
  const [camera, setCamera] = useState(null); // { status, stream }
  const [cameras, setCameras] = useState([]);
  const [cameraId, setCameraId] = useState("");
  const [checking, setChecking] = useState(true);
  const [starting, setStarting] = useState(false);
  const [error, setError] = useState("");
  const runRef = useRef(0);
  const streamRef = useRef(null);
  const startedRef = useRef(false);
  const teardownTimerRef = useRef(null);

  /** Releases the preview stream (only the camera tracks this page opened). */
  const releasePreview = () => {
    stopTracks(streamRef.current);
    streamRef.current = null;
  };

  /** One check at a time: a newer run (or leaving the page) releases an older run's stream. */
  const runCheck = (deviceId = cameraId) => {
    const run = runRef.current + 1;
    runRef.current = run;
    releasePreview();
    setCamera(null);
    setChecking(true);
    setError("");
    checkDevices(undefined, deviceId || null).then(async (result) => {
      if (runRef.current !== run) {
        stopTracks(result.camera.stream);
        return;
      }
      streamRef.current = result.camera.stream;
      setMic(result.mic);
      setCamera(result.camera);
      setChecking(false);
      const found = await listCameras();
      if (runRef.current === run) setCameras(found);
    });
  };

  // One check per visit. StrictMode's simulated unmount is absorbed by deferring the release:
  // a quick remount cancels it, so no second getUserMedia request is made.
  useEffect(() => {
    clearTimeout(teardownTimerRef.current);
    if (!startedRef.current) {
      startedRef.current = true;
      runCheck("");
    }
    return () => {
      teardownTimerRef.current = setTimeout(() => {
        runRef.current += 1;
        releasePreview();
      }, 0);
    };
    // Runs once per visit by design (see startedRef).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const micReady = mic?.status === MIC_STATUS.READY;
  const micText = mic ? MIC_TEXT[mic.status] : null;
  const cameraReady = camera?.status === CAMERA_STATUS.ON && Boolean(camera.stream);
  const cameraText = camera ? CAMERA_TEXT[camera.status] || CAMERA_TEXT[CAMERA_STATUS.ERROR] : null;

  const selectCamera = (deviceId) => {
    setCameraId(deviceId);
    runCheck(deviceId);
  };

  /** Creates the voice session (one click → one session) and opens the voice interview. */
  const continueToVoice = async () => {
    if (starting) return;
    setStarting(true);
    setError("");
    try {
      const session = await startInterview(buildStartRequest(interviewConfig, "VOICE"));
      const deviceId = cameraId || streamRef.current?.getVideoTracks()[0]?.getSettings?.().deviceId || null;
      // The test stream is released here; the interview page opens its own camera if it was allowed.
      runRef.current += 1;
      releasePreview();
      navigate(`/ai-interview/voice/${session.id}`, { state: { camera: { enabled: cameraReady, deviceId } } });
    } catch (err) {
      setError(toApiError(err).message);
      setStarting(false);
    }
  };

  const switchToText = () => {
    navigate("/ai-interview/session", { state: { ...interviewConfig, mode: "TEXT" } });
  };

  const devices = [
    {
      id: "microphone",
      name: "Microphone",
      icon: <FiMic />,
      tone: checking ? "checking" : micText?.tone,
      status: checking ? "Checking" : micText?.status,
      detail: checking
        ? "Asking your browser for microphone access…"
        : `${micText?.detail}${micReady && mic.label ? ` (${mic.label})` : ""}`
    },
    {
      id: "browser",
      name: "Voice service",
      icon: <FiMonitor />,
      tone: voiceConfigured ? "ready" : "error",
      status: voiceConfigured ? "Available" : "Voice interview unavailable",
      detail: voiceConfigured ? "The CareerAI voice interviewer is configured." : "The voice service is not configured. Use a text interview instead."
    },
    {
      id: "speaker",
      name: "Speaker",
      icon: <FiVolume2 />,
      tone: "optional",
      status: "Check your volume",
      detail: "You will hear the interviewer through your speakers or headphones. Headphones reduce echo."
    },
    {
      id: "camera",
      name: "Camera (optional)",
      icon: <FiVideo />,
      tone: checking ? "checking" : cameraText?.tone,
      status: checking ? "Checking" : cameraText?.status,
      detail: checking
        ? "Asking your browser for camera access…"
        : cameraReady
          ? cameraText.detail
          : `${cameraText?.detail} Camera is optional. You can continue with voice only.`
    }
  ];

  const canContinue = micReady && voiceConfigured && !checking && !starting;

  return (
    <div className="ai-flow-page">

      <section className="ai-flow-header">

        <div className="section-container">

          <ol className="ai-flow-stepper" aria-label="Interview progress">
            <li className="done">Setup</li>
            <li className="active">Device Check</li>
            <li>Interview</li>
            <li>Report</li>
          </ol>

          <span className="section-tag">
            DEVICE CHECK
          </span>

          <h1>Check your camera and microphone before you begin</h1>

          <p>
            The voice interview needs microphone access; the camera is optional. CareerAI asks
            your browser for permission and releases both devices when you leave this page.
          </p>

        </div>

      </section>


      <section className="ai-flow-body">

        <div className="section-container ai-flow-device-layout">

          <div className="ai-flow-device-grid" aria-live="polite">

            {devices.map((device) => (
              <article
                key={device.id}
                className={`ai-flow-device-card ${device.tone}`}
              >

                <div className="ai-flow-device-icon">
                  {device.icon}
                </div>

                <div className="ai-flow-device-info">

                  <h3>{device.name}</h3>

                  <p>{device.detail}</p>

                  <span className="ai-flow-status-badge">
                    {device.tone === "checking" && <FiLoader className="resume-spin" />}
                    {device.tone === "ready" && <FiCheckCircle />}
                    {device.tone === "optional" && <FiInfo />}
                    {(device.tone === "error" || device.tone === "warning") && <FiAlertCircle />}
                    {device.status}
                  </span>

                </div>

              </article>
            ))}

          </div>


          <div className="device-camera-preview-card">
            <div className={`device-camera-preview${cameraReady ? " on" : ""}`}>
              {cameraReady ? (
                <CameraPreview stream={camera.stream} label="Camera preview" />
              ) : (
                <div className="device-camera-preview-empty">
                  {checking ? <FiLoader className="resume-spin" aria-hidden="true" /> : <FiVideoOff aria-hidden="true" />}
                  <span>{checking ? "Opening camera preview…" : "Camera Off — you can continue with voice only."}</span>
                </div>
              )}
            </div>
            {cameras.length > 1 && (
              <label className="device-camera-select">
                Camera
                <select value={cameraId} onChange={(event) => selectCamera(event.target.value)} disabled={checking || starting}>
                  <option value="">Default camera</option>
                  {cameras.map((item) => (
                    <option key={item.deviceId} value={item.deviceId}>{item.label}</option>
                  ))}
                </select>
              </label>
            )}
            <p className="device-camera-privacy">
              <FiInfo aria-hidden="true" />
              Your camera preview stays on your device and is not recorded or analyzed.
            </p>
          </div>

          <div className={`ai-flow-ready-card${checking ? " checking" : ""}`}>

            <div className="ai-flow-ready-status">

              <div className="ai-flow-ready-icon">
                {checking ? <FiLoader className="resume-spin" /> : canContinue ? <FiCheckCircle /> : <FiAlertCircle />}
              </div>

              <div>
                <h2>
                  {checking ? "Checking your devices…" : canContinue ? (cameraReady ? "Microphone and Camera Ready" : "Microphone Ready") : micText?.status || "Voice interview unavailable"}
                </h2>
                <p>
                  {checking
                    ? "Your browser may ask for permission."
                    : canContinue
                      ? cameraReady
                        ? "You're ready to begin your voice interview."
                        : "You're ready to begin. Camera is optional. You can continue with voice only."
                      : mic?.status === MIC_STATUS.DENIED || mic?.status === MIC_STATUS.NOT_FOUND
                        ? "Voice interview cannot continue without a microphone. Fix it and retry, or take the interview as text."
                        : "Fix the issue and retry, or take the interview as text."}
                </p>
              </div>

            </div>

            <div className="ai-flow-ready-summary">

              <span>INTERVIEW</span>

              <strong>{interviewConfig.targetRole}</strong>

              <p>
                {interviewConfig.source} • {interviewConfig.category} • {interviewConfig.difficulty}
                {interviewConfig.questionCount ? ` • ${interviewConfig.questionCount} questions` : ""}
              </p>

            </div>

          </div>

          {error && (
            <p className="ai-flow-session-notice error" role="alert">
              <FiAlertCircle />
              {error}
            </p>
          )}


          <div className="ai-flow-device-actions">

            <Link
              to="/ai-interview/setup"
              state={interviewConfig}
              className="ai-flow-back-link"
            >
              <FiArrowLeft />
              Back
            </Link>

            <div className="ai-flow-device-actions-main">

              <button
                type="button"
                className="ai-flow-outline-button"
                onClick={() => runCheck()}
                disabled={checking || starting}
              >
                <FiRefreshCw />
                Retry
              </button>

              <button
                type="button"
                className="ai-flow-outline-button"
                onClick={switchToText}
                disabled={starting}
              >
                <FiType />
                Switch to Text Interview
              </button>

              <button
                type="button"
                className="ai-primary-button"
                onClick={continueToVoice}
                disabled={!canContinue}
              >
                {starting ? "Starting…" : "Continue to Voice Interview"}
                {starting ? <FiLoader className="resume-spin" /> : <FiArrowRight />}
              </button>

            </div>

          </div>

        </div>

      </section>

    </div>
  );
}

export default DeviceCheck;
