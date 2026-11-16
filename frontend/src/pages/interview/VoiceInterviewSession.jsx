import { useEffect, useRef, useState } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";
import {
  FiClock,
  FiMic,
  FiMicOff,
  FiPhoneOff,
  FiAlertCircle,
  FiLoader,
  FiArrowRight,
  FiType,
  FiRefreshCw,
  FiVideo,
  FiVideoOff
} from "react-icons/fi";
import CandidateCamera from "../../components/interview/CandidateCamera";
import SimliInterviewerAvatar from "../../components/interview/SimliInterviewerAvatar";
import { toApiError } from "../../services/api";
import { getInterview, getInterviewReport, submitVoiceTranscript } from "../../services/interviewService";
import {
  CONNECT_ERROR_MESSAGE,
  VOICE_STATES,
  VapiInterviewClient,
  buildVariableValues,
  getVapiConfig,
  useVapiState
} from "../../services/vapiService";
import { AVATAR_STARTUP_WAIT_MS, SimliAvatarClient, useAvatarState } from "../../services/simliService";
import { CAMERA_STATUS, CandidateCameraController, useCameraState } from "../../services/candidateCamera";

const TYPE_LABELS = {
  TECHNICAL: "Technical",
  HR: "HR",
  BEHAVIORAL: "Behavioral",
  RESUME: "Resume-Based",
  JOB_SPECIFIC: "Job-Specific",
  MIXED: "Mixed"
};

const STATE_LABELS = {
  [VOICE_STATES.IDLE]: "Preparing your interviewer…",
  [VOICE_STATES.CONNECTING]: "Connecting to the interviewer…",
  [VOICE_STATES.SPEAKING]: "Interviewer speaking",
  [VOICE_STATES.LISTENING]: "Listening to you",
  [VOICE_STATES.THINKING]: "Interviewer thinking…",
  [VOICE_STATES.ENDED]: "Call ended",
  [VOICE_STATES.ERROR]: "Connection problem"
};

// If the SDK does not emit call-end after an explicit stop, finalise after this delay.
const MANUAL_END_FALLBACK_MS = 6000;

function formatTime(totalSeconds) {
  return `${String(Math.floor(totalSeconds / 60)).padStart(2, "0")}:${String(totalSeconds % 60).padStart(2, "0")}`;
}

function title(text) {
  return text ? text.charAt(0) + text.slice(1).toLowerCase() : "";
}

/** Setup-page state for retrying or switching to text with the same settings. */
function setupState(session, mode) {
  const sourceByType = { RESUME: "Resume-Based", JOB_SPECIFIC: "Job-Based" };
  const categoryByType = { HR: "HR", BEHAVIORAL: "Behavioural", MIXED: "Mixed" };
  return {
    source: sourceByType[session?.type] || "Role-Based",
    category: categoryByType[session?.type] || "Technical",
    targetRole: session?.targetRole,
    difficulty: title(session?.difficulty),
    resumeId: session?.resumeId || null,
    focusTopics: session?.focusTopics || [],
    mode
  };
}

function VoiceInterviewSession() {
  const { id } = useParams();
  const navigate = useNavigate();
  // Camera choice from the device check ({ enabled, deviceId }); the camera is optional.
  const cameraPreference = useLocation().state?.camera || null;
  const sessionId = Number(id) || null;

  const [session, setSession] = useState(null);
  const [phase, setPhase] = useState("loading"); // loading | live | submitting | cancelled | error | unavailable
  const [message, setMessage] = useState("");
  const [seconds, setSeconds] = useState(0);
  const [client, setClient] = useState(null);
  const state = useVapiState(client);
  // Visual layer only: the Simli avatar follows the Vapi call and never controls it.
  const [avatarClient, setAvatarClient] = useState(null);
  const avatar = useAvatarState(avatarClient);
  // Local self-view only: independent of the call and the avatar (never sent anywhere).
  const [camera] = useState(() => new CandidateCameraController({ deviceId: cameraPreference?.deviceId || null }));
  const cameraState = useCameraState(camera);

  const clientRef = useRef(null);
  const startedRef = useRef(false);
  const finalizingRef = useRef(false);
  const teardownTimerRef = useRef(null);
  const unmountedRef = useRef(false);
  const manualEndTimerRef = useRef(null);
  const transcriptEndRef = useRef(null);
  const avatarRef = useRef(null);
  const videoRef = useRef(null);
  const audioRef = useRef(null);
  const callStartRef = useRef(false);
  const cameraStartedRef = useRef(false);

  /** Submits the final transcript exactly once and moves to the report. */
  const finalize = async () => {
    if (finalizingRef.current) return;
    finalizingRef.current = true;
    clearTimeout(manualEndTimerRef.current);
    avatarRef.current?.stop();
    camera.destroy();

    const current = clientRef.current?.getState() || {};
    setPhase("submitting");

    for (let attempt = 1; attempt <= 3; attempt += 1) {
      try {
        const result = await submitVoiceTranscript(sessionId, { providerCallId: current.callId, transcript: current.transcript || [] });
        if (result.status === "COMPLETED") {
          navigate(`/ai-interview/report?id=${sessionId}`, { replace: true });
        } else {
          setMessage(result.message || "No answers were captured, so no report was created.");
          setPhase("cancelled");
        }
        return;
      } catch (error) {
        const { status, message: text } = toApiError(error);
        if (status === 409) {
          // Already being processed (e.g. a duplicate submit): wait for the stored report.
          for (let poll = 0; poll < 6; poll += 1) {
            await new Promise((resolve) => setTimeout(resolve, 2000));
            try {
              await getInterviewReport(sessionId);
              navigate(`/ai-interview/report?id=${sessionId}`, { replace: true });
              return;
            } catch {
              // keep waiting
            }
          }
        }
        if (attempt === 3 || (status >= 400 && status < 500 && status !== 409)) {
          setMessage(`Your interview could not be saved: ${text}`);
          setPhase("error");
          return;
        }
        await new Promise((resolve) => setTimeout(resolve, 1500 * attempt));
      }
    }
  };

  // Load the session and create ONE voice client and ONE avatar client (the call itself starts in
  // the startup effect below). StrictMode's simulated unmount is absorbed by deferring teardown:
  // a quick remount cancels it, a real unmount stops the call and the avatar.
  useEffect(() => {
    clearTimeout(teardownTimerRef.current);
    if (startedRef.current) return scheduleTeardown;
    startedRef.current = true;

    const config = getVapiConfig();

    getInterview(sessionId)
      .then((data) => {
        // The page was left before the session loaded: never start a call in the background.
        if (unmountedRef.current) return;
        setSession(data);
        if (data.mode !== "VOICE") {
          navigate(`/ai-interview/session?id=${data.id}`, { replace: true });
          return;
        }
        if (data.status === "COMPLETED") {
          navigate(`/ai-interview/report?id=${data.id}`, { replace: true });
          return;
        }
        if (data.status !== "IN_PROGRESS") {
          setMessage("This voice interview has already ended without a report.");
          setPhase("cancelled");
          return;
        }
        if (!config.available) {
          console.info("[VAPI] config", { publicKeyPresent: Boolean(config.publicKey), assistantIdPresent: Boolean(config.assistantId) });
          setPhase("unavailable");
          return;
        }

        const voiceClient = new VapiInterviewClient({ publicKey: config.publicKey, assistantId: config.assistantId, interviewId: data.id });
        voiceClient.onEnded(() => finalize());
        clientRef.current = voiceClient;
        setClient(voiceClient);
        const interviewerAvatar = new SimliAvatarClient({ interviewId: data.id });
        avatarRef.current = interviewerAvatar;
        setAvatarClient(interviewerAvatar);
        setPhase("live");
      })
      .catch((error) => {
        setMessage(toApiError(error).message);
        setPhase("error");
      });

    return scheduleTeardown;

    function scheduleTeardown() {
      teardownTimerRef.current = setTimeout(() => {
        unmountedRef.current = true;
        clearTimeout(manualEndTimerRef.current);
        camera.destroy();
        avatarRef.current?.destroy();
        clientRef.current?.destroy();
      }, 0);
    }
    // Runs once per page load by design (see startedRef).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Elapsed time while connected.
  useEffect(() => {
    if (!state.isConnected) return undefined;
    const timer = setInterval(() => setSeconds((value) => value + 1), 1000);
    return () => clearInterval(timer);
  }, [state.isConnected]);

  // Startup, once per interview: the avatar's single initial session starts connecting first, the
  // call starts as soon as the avatar shows its first frame (or fails), and never later than
  // AVATAR_STARTUP_WAIT_MS — the interview never depends on the avatar.
  useEffect(() => {
    if (phase !== "live" || !client || !avatarClient || !session || callStartRef.current) return;
    if (!videoRef.current || !audioRef.current) return;
    callStartRef.current = true;
    avatarClient.connect({ videoElement: videoRef.current, audioElement: audioRef.current });
    avatarClient.whenSettled(AVATAR_STARTUP_WAIT_MS).then((avatarStatus) => {
      if (unmountedRef.current) return;
      console.info("[VAPI] starting call", { interviewId: session.id, avatar: avatarStatus });
      client.start(buildVariableValues(session.voiceContext));
    });
  }, [phase, client, avatarClient, session]);

  // Camera allowed in the device check: open the self-view once (a failure just shows Camera Off).
  useEffect(() => {
    if (phase !== "live" || !cameraPreference?.enabled || cameraStartedRef.current) return;
    cameraStartedRef.current = true;
    camera.turnOn();
  }, [phase, cameraPreference, camera]);

  // Keeps the avatar on the current media elements if the live view was remounted (e.g. after a
  // failed call start and Retry). Never opens a session.
  useEffect(() => {
    avatarClient?.attachElements({ videoElement: videoRef.current, audioElement: audioRef.current });
  });

  useEffect(() => {
    avatarClient?.setAudioTrack(state.assistantAudioTrack);
  }, [avatarClient, state.assistantAudioTrack]);

  useEffect(() => {
    if (state.status !== VOICE_STATES.ENDED) return;
    avatarClient?.stop();
    camera.turnOff();
  }, [avatarClient, camera, state.status]);

  useEffect(() => {
    transcriptEndRef.current?.scrollIntoView({ behavior: "smooth", block: "nearest" });
  }, [state.transcript.length, state.partial]);

  const endInterview = async () => {
    if (!clientRef.current || finalizingRef.current) return;
    if (!window.confirm("End the interview now and get your report?")) return;
    // call-end normally finalises; the timer covers SDKs that do not emit it after stop().
    manualEndTimerRef.current = setTimeout(() => finalize(), MANUAL_END_FALLBACK_MS);
    avatarRef.current?.stop();
    camera.turnOff();
    await clientRef.current.stop();
  };

  const retryConnection = () => {
    // Same CareerAI session and same SDK client: retry() releases the failed attempt and starts a new call.
    if (!clientRef.current) return;
    finalizingRef.current = false;
    setSeconds(0);
    setPhase("live");
    clientRef.current.retry(buildVariableValues(session?.voiceContext));
  };

  const lastOf = (role) => [...state.transcript].reverse().find((item) => item.role === role)?.text;
  const latestInterviewer = state.partial?.role === "assistant" ? state.partial.text : lastOf("assistant");
  const latestCandidate = state.partial?.role === "user" ? state.partial.text : lastOf("user");
  const callFailed = phase === "live" && state.status === VOICE_STATES.ERROR && !state.isConnected;
  const cameraOn = cameraState.status === CAMERA_STATUS.ON || cameraState.status === CAMERA_STATUS.STARTING;
  const toggleCamera = () => (cameraOn ? camera.turnOff() : camera.turnOn());


  /* ---------- non-live screens ---------- */

  if (phase !== "live" || callFailed) {
    const screens = {
      loading: { icon: <FiLoader className="resume-spin" />, title: "Preparing your voice interview…", text: "Loading your interview settings." },
      submitting: { icon: <FiLoader className="resume-spin" />, title: "Preparing your report…", text: "Saving your transcript and evaluating your answers. This can take up to a minute." },
      cancelled: { icon: <FiAlertCircle />, title: "No report for this call", text: message },
      unavailable: { icon: <FiAlertCircle />, title: "Voice interview unavailable", text: "The voice service is not configured on this device. You can take the same interview as text." },
      error: { icon: <FiAlertCircle />, title: "Something went wrong", text: message }
    };
    const screen = callFailed
      ? {
        icon: <FiAlertCircle />,
        title: CONNECT_ERROR_MESSAGE,
        text: state.error && state.error !== CONNECT_ERROR_MESSAGE
          ? state.error
          : "Check your internet connection and try again, or take this interview as text."
      }
      : screens[phase];
    const busy = phase === "loading" || phase === "submitting";

    return (
      <div className="ai-flow-page">
        <section className="ai-flow-body">
          <div className="section-container ai-flow-session-message" role={busy ? "status" : "alert"}>
            {screen.icon}
            <h1>{screen.title}</h1>
            <p>{screen.text}</p>
            {!busy && (
              <div className="ai-flow-report-cta-actions">
                {callFailed ? (
                  <button type="button" className="ai-primary-button" onClick={retryConnection}>
                    <FiRefreshCw />
                    Retry
                  </button>
                ) : (
                  session && phase !== "unavailable" && (
                    <Link to="/ai-interview/setup" state={setupState(session, "VOICE")} className="ai-primary-button">
                      <FiRefreshCw />
                      Retry Voice Interview
                    </Link>
                  )
                )}
                <Link to="/ai-interview/setup" state={setupState(session, "TEXT")} className="ai-flow-outline-button">
                  <FiType />
                  Switch to Text Interview
                </Link>
              </div>
            )}
          </div>
        </section>
      </div>
    );
  }


  /* ---------- live call ---------- */

  return (
    <div className="ai-flow-session">

      <header className="ai-flow-session-topbar">

        <div className="ai-flow-session-title">
          <span className="ai-live-dot"></span>
          <div>
            <h1>{TYPE_LABELS[session.type] || session.type} Voice Interview</h1>
            <strong>{session.targetRole}</strong>
            <span>
              Voice interview • {title(session.difficulty)}
              {session.resumeTitle ? ` • ${session.resumeTitle}` : ""}
            </span>
          </div>
        </div>

        <div className="ai-flow-session-meta">
          <span className={`voice-connection ${state.isConnected ? "on" : "off"}`}>
            {state.isConnected ? "Connected" : state.status === VOICE_STATES.CONNECTING ? "Connecting" : state.status === VOICE_STATES.IDLE ? "Preparing" : "Disconnected"}
          </span>
          <span className="ai-flow-timer">
            <FiClock />
            {formatTime(seconds)}
          </span>
          <button type="button" className="ai-flow-end-button" onClick={endInterview} disabled={!state.isConnected} aria-label="End interview">
            <FiPhoneOff />
            End Interview
          </button>
        </div>

      </header>

      {state.error && (
        <p className="ai-flow-session-notice error" role="alert">
          <FiAlertCircle />
          {state.error}
        </p>
      )}

      <div className="ai-flow-session-grid">

        <section className="ai-flow-stage">

          <div className="voice-stage-tiles">

            <SimliInterviewerAvatar
              voiceState={state.status}
              stateLabel={STATE_LABELS[state.status]}
              speaking={state.status === VOICE_STATES.SPEAKING}
              avatar={avatar}
              videoRef={videoRef}
              audioRef={audioRef}
              onRetry={() => avatarClient?.retry()}
            >
              <CandidateCamera
                controller={camera}
                name={session.voiceContext?.candidateName}
                muted={state.isMuted}
                speaking={state.isUserSpeaking}
              />
            </SimliInterviewerAvatar>

          </div>

          <div className="ai-flow-question-card">
            <span>INTERVIEWER</span>
            <p>{latestInterviewer || (state.isConnected ? "The interviewer will start shortly…" : "Connecting…")}</p>
          </div>

          <div className="voice-candidate-card">
            <span>YOU</span>
            <p>{latestCandidate || "Answer out loud when the interviewer asks a question."}</p>
          </div>

          <div className="ai-flow-controls">
            <p className="ai-flow-answer-helper">
              Speak naturally. Your answers are evaluated on their content only — not your voice or accent.
            </p>
            <div className="voice-control-buttons">
              <button
                type="button"
                className="ai-flow-outline-button"
                onClick={() => client?.setMuted(!state.isMuted)}
                disabled={!state.isConnected}
                aria-pressed={state.isMuted}
                aria-label={state.isMuted ? "Unmute microphone" : "Mute microphone"}
              >
                {state.isMuted ? <FiMic /> : <FiMicOff />}
                {state.isMuted ? "Unmute" : "Mute"}
              </button>
              <button
                type="button"
                className="ai-flow-outline-button"
                onClick={toggleCamera}
                disabled={cameraState.status === CAMERA_STATUS.STARTING}
                aria-pressed={!cameraOn}
                aria-label={cameraOn ? "Turn camera off" : "Turn camera on"}
              >
                {cameraOn ? <FiVideoOff /> : <FiVideo />}
                {cameraOn ? "Turn Camera Off" : "Turn Camera On"}
              </button>
            </div>
          </div>

          {cameraState.error && (
            <p className="voice-camera-notice" role="status">
              <FiVideoOff aria-hidden="true" />
              {cameraState.error}
            </p>
          )}

        </section>

        <aside className="ai-flow-transcript">

          <h2>Live transcript</h2>

          <div className="ai-flow-transcript-list">
            {state.transcript.map((item, index) => (
              <div key={`${index}-${item.role}`} className={`ai-flow-transcript-item ${item.role === "assistant" ? "ai" : "candidate"}`}>
                <strong>{item.role === "assistant" ? "AI Interviewer" : "You"}</strong>
                <p>{item.text}</p>
              </div>
            ))}
            {state.partial && (
              <div className={`ai-flow-transcript-item ${state.partial.role === "assistant" ? "ai" : "candidate"} partial`}>
                <strong>{state.partial.role === "assistant" ? "AI Interviewer" : "You"}</strong>
                <p>{state.partial.text}…</p>
              </div>
            )}
            <div ref={transcriptEndRef}></div>
          </div>

          <Link to="/ai-interview/setup" className="voice-switch-link">
            Prefer typing? Start a text interview
            <FiArrowRight />
          </Link>

        </aside>

      </div>

    </div>
  );
}

export default VoiceInterviewSession;
