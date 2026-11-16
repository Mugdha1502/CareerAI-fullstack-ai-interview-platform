import { useSyncExternalStore } from "react";
import VapiImport from "@vapi-ai/web";

/* =====================================================
   VAPI VOICE SERVICE — the only place that talks to the @vapi-ai/web SDK.

   - Reads the PUBLIC key and assistant id from Vite env (VITE_VAPI_PUBLIC_KEY,
     VITE_VAPI_ASSISTANT_ID). The public key is meant for browsers; it is never
     logged, displayed or sent to the CareerAI backend. No private key exists here.
   - Starts the saved assistant with SDK 3.0.1's signature:
       vapi.start(assistantId, { variableValues })   // assistantOverrides.variableValues
   - Uses ONE SDK instance per interview page; a failed start can be retried on the
     same instance (the SDK cleans itself up and resolves start() to null on failure).
   - Logs safe diagnostics with the [VAPI] prefix (presence flags, masked ids,
     variable keys, error name/code/status/type) — never keys, tokens or user content.
===================================================== */

/*
 * @vapi-ai/web 3.0.1 is CommonJS only: dist/vapi.js sets `exports.default = Vapi` (with __esModule).
 * Vite 8's dependency pre-bundle exports the whole CommonJS exports object as the default
 * (`export default require_vapi()`), so the default import is { __esModule, default: Vapi } in dev
 * and calling `new` on it throws "Vapi is not a constructor". Resolve the class itself.
 */
export function resolveVapiConstructor(imported) {
  if (typeof imported === "function") return imported;
  if (imported && typeof imported.default === "function") return imported.default;
  return null;
}

const Vapi = resolveVapiConstructor(VapiImport);

// Temporary diagnostic (safe: logs only the export type, never keys).
console.log("[VAPI] sdk export type", typeof Vapi);

/** Standardised UI states (also the source of truth for the avatar's status label). */
export const VOICE_STATES = {
  IDLE: "IDLE",
  CONNECTING: "CONNECTING",
  SPEAKING: "SPEAKING",
  LISTENING: "LISTENING",
  THINKING: "THINKING",
  ENDED: "ENDED",
  ERROR: "ERROR"
};

export const CONNECT_ERROR_MESSAGE = "Could not connect to the voice interviewer.";

export function getVapiConfig() {
  const publicKey = import.meta.env.VITE_VAPI_PUBLIC_KEY;
  const assistantId = import.meta.env.VITE_VAPI_ASSISTANT_ID;
  return { available: Boolean(publicKey && assistantId), publicKey, assistantId };
}

export function isVoiceAvailable() {
  return getVapiConfig().available;
}

/** "fd3d89...fc532f" style mask for logs. */
export function maskId(value) {
  const text = String(value || "");
  if (!text) return "(missing)";
  return text.length <= 12 ? `${text.slice(0, 2)}...` : `${text.slice(0, 6)}...${text.slice(-6)}`;
}


/* ---------- Safe diagnostics ---------- */

// Anything that looks like a key, token or bearer value is redacted from logged messages.
const SECRET_PATTERN = /(bearer\s+[\w.-]+|eyJ[\w-]+\.[\w-]+\.[\w-]+|\b(pk|sk)_[\w-]{6,}|\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b)/gi;

function redact(text) {
  return String(text).replace(SECRET_PATTERN, "[redacted]").slice(0, 300);
}

/**
 * Safe fields of an SDK error/event: name, code, message, status, type, stage.
 * Handles Error objects, Vapi HTTP error bodies ({ message, error, statusCode }) and SDK events.
 */
export function safeErrorFields(error) {
  if (error == null) return {};
  if (typeof error === "string") return { message: redact(error) };

  const inner = typeof error.error === "object" && error.error !== null ? error.error : null;
  const pick = (...values) => values.find((value) => value !== undefined && value !== null && value !== "");
  const rawMessage = pick(
    typeof error.error === "string" ? error.error : undefined,
    inner?.message,
    inner?.errorMsg,
    error.message,
    error.errorMsg
  );
  const message = Array.isArray(rawMessage)
    ? rawMessage.join("; ")
    : typeof rawMessage === "object" && rawMessage !== null
      ? pick(rawMessage.message, rawMessage.error, JSON.stringify(rawMessage))
      : rawMessage;

  const fields = {
    name: pick(inner?.name, error.name),
    code: pick(inner?.code, error.code),
    status: pick(inner?.statusCode, inner?.status, error.statusCode, error.status, rawMessage?.statusCode),
    type: pick(error.type, inner?.type),
    stage: error.stage,
    message: message === undefined ? undefined : redact(message)
  };
  return Object.fromEntries(Object.entries(fields).filter(([, value]) => value !== undefined));
}

function vapiLog(event, fields) {
  if (fields) console.info(`[VAPI] ${event}`, fields);
  else console.info(`[VAPI] ${event}`);
}

function vapiWarn(event, fields) {
  console.warn(`[VAPI] ${event}`, fields);
}


/* ---------- Dynamic variables ---------- */

const TEMPLATE_DELIMITERS = /\{\{|\}\}|\{%|%\}/g;

/** Neutralises template delimiters and control characters; limits length. Never returns undefined. */
export function sanitizeVariable(value, max = 400) {
  const text = String(value ?? "")
    .replace(TEMPLATE_DELIMITERS, " ")
    // eslint-disable-next-line no-control-regex
    .replace(/[\u0000-\u001f\u007f]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
  return text.length > max ? `${text.slice(0, max).trim()}...` : text;
}

/**
 * Variable values for the assistant's {{placeholders}} from the backend's compact voiceContext.
 * Every key is always a string (empty when unknown); resume and job description are short summaries.
 */
export function buildVariableValues(context) {
  const ctx = context || {};
  return {
    candidateName: sanitizeVariable(ctx.candidateName || "there", 40),
    targetRole: sanitizeVariable(ctx.targetRole, 120),
    interviewType: sanitizeVariable(ctx.interviewType, 30),
    difficulty: sanitizeVariable(ctx.difficulty, 20),
    experienceLevel: sanitizeVariable(ctx.experienceLevel, 60),
    focusTopics: sanitizeVariable(ctx.focusTopics, 400),
    resumeContext: sanitizeVariable(ctx.resumeContext, 900),
    jobDescriptionContext: sanitizeVariable(ctx.jobDescriptionContext, 1200),
    questionCount: String(Math.min(10, Math.max(1, Number(ctx.questionCount) || 5)))
  };
}


/* ---------- Transcript handling (pure, unit-testable) ---------- */

/** { kind: "partial" | "final", role, text } for transcript messages, otherwise null. */
export function parseTranscriptMessage(message) {
  if (!message || message.type !== "transcript") return null;
  const role = message.role === "assistant" || message.role === "user" ? message.role : null;
  const text = typeof message.transcript === "string" ? message.transcript.replace(/\s+/g, " ").trim() : "";
  if (!role || !text) return null;
  return { kind: message.transcriptType === "final" ? "final" : "partial", role, text };
}

/** Appends a FINAL entry unless it repeats one of the last few entries of the same role. */
export function appendFinalTranscript(transcript, entry, timestamp = new Date().toISOString()) {
  const recent = transcript.slice(-4);
  if (recent.some((item) => item.role === entry.role && item.text === entry.text)) {
    return transcript;
  }
  return [...transcript, { role: entry.role, text: entry.text, timestamp }];
}

/** Safe, user-facing message for SDK errors (never the raw payload or keys). */
export function friendlyVoiceError(error) {
  const { message = "", name = "", type = "", status } = safeErrorFields(error);
  const raw = `${name} ${type} ${message} ${status ?? ""}`.toLowerCase();
  if (raw.includes("permission") || raw.includes("notallowed") || raw.includes("denied")) {
    return "Microphone permission was denied. Allow microphone access in your browser and try again.";
  }
  if (raw.includes("notfound") || raw.includes("no microphone") || raw.includes("requested device")) {
    return "No microphone was found. Connect a microphone and try again.";
  }
  return CONNECT_ERROR_MESSAGE;
}

/**
 * SDK `error` events that end a start attempt. Others (audio-processing, observer, camera,
 * recording setup) are non-fatal: the SDK continues the call, so they are only logged.
 */
function isFatalStartError(error) {
  const type = String(error?.type || "");
  return type === "validation-error"
    || type === "start-method-error"
    || type.startsWith("microphone-preflight")
    || type === "daily-call-object-creation-error"
    || type === "daily-call-join-error"
    || type === "daily-error";
}


/* ---------- Client ---------- */

const INITIAL_STATE = {
  status: VOICE_STATES.IDLE,
  isConnected: false,
  isAssistantSpeaking: false,
  isUserSpeaking: false,
  isMuted: false,
  callId: null,
  transcript: [],
  partial: null,
  error: null,
  attempt: 0,
  // The assistant's remote audio track (from the SDK's public `audio` event); drives the avatar.
  assistantAudioTrack: null
};

/** The audio track playing in the SDK's assistant <audio> element, or null. */
export function assistantTrackOf(player) {
  const stream = player?.srcObject;
  const tracks = stream && typeof stream.getAudioTracks === "function" ? stream.getAudioTracks() : [];
  return tracks.find((track) => track.readyState !== "ended") || null;
}

/**
 * Wraps the voice call for one interview page.
 * - One SDK instance and one listener set for the client's lifetime.
 * - start() is ignored while a start is in flight or a call is live (StrictMode / double clicks),
 *   but a FAILED attempt can be retried explicitly with retry().
 * - The end callback fires once per established call.
 * createVapi is injectable for tests (defaults to the real SDK).
 */
export class VapiInterviewClient {
  constructor({ publicKey, assistantId, vapiConstructor = Vapi, createVapi, interviewId = null } = {}) {
    this.vapiConstructor = vapiConstructor;
    // Default factory: the real SDK class (`new Vapi(publicKey)`); tests may inject either option.
    createVapi = createVapi || ((key) => {
      if (typeof this.vapiConstructor !== "function") {
        throw new TypeError("Voice SDK failed to load (constructor unavailable).");
      }
      return new this.vapiConstructor(key);
    });
    this.publicKey = publicKey;
    this.assistantId = assistantId;
    this.createVapi = createVapi;
    this.interviewId = interviewId;
    this.state = INITIAL_STATE;
    this.listeners = new Set();
    this.handlers = [];
    this.vapi = null;
    this.starting = false;
    this.callEstablished = false;
    this.endedNotified = false;
    this.destroyed = false;
    this.onEnd = null;
    this.lastVariables = null;
    // Why the current call ended (diagnostics only).
    this.stopRequested = false;
    this.endedReason = null;
    this.ejected = false;

    this.subscribe = this.subscribe.bind(this);
    this.getState = this.getState.bind(this);

    vapiLog("config", {
      publicKeyPresent: Boolean(publicKey),
      assistantIdPresent: Boolean(assistantId),
      assistantId: maskId(assistantId)
    });
  }

  subscribe(listener) {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  getState() {
    return this.state;
  }

  setState(patch) {
    this.state = { ...this.state, ...patch };
    this.listeners.forEach((listener) => listener());
  }

  /** Called once when an established call ends (normally, by the user, or after an error mid-call). */
  onEnded(callback) {
    this.onEnd = callback;
  }

  notifyEnded() {
    if (this.endedNotified) return;
    this.endedNotified = true;
    this.onEnd?.(this.state);
  }

  /** True while a start is in flight or a call is live: further start() calls are ignored. */
  isBusy() {
    return this.starting || this.state.isConnected;
  }

  async start(variableValues) {
    if (this.destroyed || this.isBusy()) return;
    if (this.state.status !== VOICE_STATES.IDLE && this.state.status !== VOICE_STATES.ERROR) return;
    await this.startCall(variableValues);
  }

  /** Explicit retry after a failed start: same SDK instance, same listeners, new call attempt. */
  async retry(variableValues = this.lastVariables) {
    vapiLog("retry clicked", { interviewId: this.interviewId, status: this.state.status, busy: this.isBusy() });
    if (this.destroyed || this.isBusy()) return;
    if (this.vapi) {
      // Release anything a previous attempt left behind (e.g. a Daily call object) before starting again.
      try {
        await this.vapi.stop();
      } catch {
        // nothing to stop
      }
    }
    await this.startCall(variableValues);
  }

  async startCall(variableValues) {
    this.starting = true;
    this.callEstablished = false;
    this.endedNotified = false;
    this.stopRequested = false;
    this.endedReason = null;
    this.ejected = false;
    this.lastVariables = variableValues;
    const attempt = this.state.attempt + 1;
    this.setState({
      ...INITIAL_STATE,
      status: VOICE_STATES.CONNECTING,
      attempt
    });

    if (!this.publicKey || !this.assistantId) {
      this.starting = false;
      vapiWarn("start blocked", { reason: "missing configuration", publicKeyPresent: Boolean(this.publicKey), assistantIdPresent: Boolean(this.assistantId) });
      this.setState({ status: VOICE_STATES.ERROR, error: "Voice interview unavailable: the voice service is not configured." });
      return;
    }

    vapiLog("start attempt", {
      attempt,
      interviewId: this.interviewId,
      assistantId: maskId(this.assistantId),
      mode: "VOICE",
      variableKeys: Object.keys(variableValues || {})
    });

    try {
      if (!this.vapi) {
        this.vapi = this.createVapi(this.publicKey);
        this.attach();
      }
      // SDK 3.0.1: start(assistant, assistantOverrides, squad, workflow, workflowOverrides, options)
      const call = await this.vapi.start(this.assistantId, { variableValues });
      if (this.destroyed) return;

      if (!call) {
        // The SDK resolves null on failure (after emitting call-start-failed / error).
        if (this.state.status !== VOICE_STATES.ERROR) {
          vapiWarn("start returned no call", { attempt });
          this.setState({ status: VOICE_STATES.ERROR, isConnected: false, error: this.state.error || CONNECT_ERROR_MESSAGE });
        }
        return;
      }

      vapiLog("call created", { attempt, callId: maskId(call.id) });
      if (call.id) this.setState({ callId: call.id });
    } catch (error) {
      vapiWarn("start threw", safeErrorFields(error));
      this.setState({ status: VOICE_STATES.ERROR, isConnected: false, error: friendlyVoiceError(error) });
    } finally {
      this.starting = false;
    }
  }

  attach() {
    const on = (event, handler) => {
      this.vapi.on(event, handler);
      this.handlers.push([event, handler]);
    };

    on("call-start", () => {
      vapiLog("call-start", { interviewId: this.interviewId });
      this.callEstablished = true;
      this.setState({
        isConnected: true,
        error: null,
        status: this.state.isAssistantSpeaking ? VOICE_STATES.SPEAKING : VOICE_STATES.LISTENING
      });
    });

    on("call-end", () => {
      vapiLog("call-end", { interviewId: this.interviewId, established: this.callEstablished, finalEntries: this.state.transcript.length });
      const wasEstablished = this.callEstablished;
      if (wasEstablished && !this.stopRequested && !this.destroyed) {
        // Nobody on this page ended the call: Vapi/Daily ended it (endedReason says why).
        vapiWarn("unexpected call termination", {
          interviewId: this.interviewId,
          endedReason: this.endedReason || "unknown",
          ejected: this.ejected,
          finalEntries: this.state.transcript.length
        });
      }
      this.callEstablished = false;
      this.setState({
        isConnected: false,
        isAssistantSpeaking: false,
        isUserSpeaking: false,
        partial: null,
        assistantAudioTrack: null,
        status: wasEstablished ? VOICE_STATES.ENDED : this.state.status
      });
      // Only a call that actually started produces a transcript to submit.
      if (wasEstablished) this.notifyEnded();
    });

    // SDK 3.0.1 emits the <audio> element that plays the assistant once its track arrives
    // (again after a renegotiation). Playback is left untouched; only the track is exposed.
    on("audio", (player) => {
      const track = assistantTrackOf(player);
      vapiLog("assistant audio", { track: Boolean(track) });
      if (track !== this.state.assistantAudioTrack) this.setState({ assistantAudioTrack: track });
    });

    // speech-start / speech-end describe the assistant's speech.
    on("speech-start", () => this.setState({ isAssistantSpeaking: true, status: VOICE_STATES.SPEAKING }));
    on("speech-end", () => this.setState({ isAssistantSpeaking: false, status: this.state.isConnected ? VOICE_STATES.LISTENING : this.state.status }));

    on("message", (message) => {
      // Vapi's server reports why a call ended (e.g. silence-timed-out, assistant-ended-call).
      // The SDK also synthesises "customer-ended-call" after leaving, so keep the first reason.
      if (message?.type === "status-update" && message.status === "ended") {
        if (!this.endedReason) this.endedReason = sanitizeVariable(message.endedReason, 80) || "unknown";
        vapiLog("status ended", { interviewId: this.interviewId, endedReason: this.endedReason });
        return;
      }
      const event = parseTranscriptMessage(message);
      if (!event) return;

      if (event.kind === "partial") {
        this.setState({ partial: event, isUserSpeaking: event.role === "user" ? true : this.state.isUserSpeaking });
        return;
      }

      const patch = { transcript: appendFinalTranscript(this.state.transcript, event), partial: null };
      if (event.role === "user") {
        patch.isUserSpeaking = false;
        // The candidate finished a sentence; the interviewer is preparing a reply.
        if (!this.state.isAssistantSpeaking && this.state.isConnected) patch.status = VOICE_STATES.THINKING;
      }
      this.setState(patch);
    });

    on("call-start-failed", (event) => {
      vapiWarn("call-start-failed", { attempt: this.state.attempt, ...safeErrorFields(event) });
      this.starting = false;
      this.setState({ status: VOICE_STATES.ERROR, isConnected: false, error: friendlyVoiceError(event) });
    });

    on("error", (error) => {
      const fields = safeErrorFields(error);
      if (this.state.isConnected) {
        if (/ejected|meeting has ended/i.test(fields.message || "")) {
          // Daily removes participants when the room is closed server-side: the call is ending,
          // call-end follows and finalises as usual.
          this.ejected = true;
          vapiLog("meeting ended by server", { interviewId: this.interviewId, stopRequested: this.stopRequested, ...fields });
          return;
        }
        // During a live call: keep the call and the transcript; call-end will finalise once.
        vapiWarn("error (during call)", fields);
        return;
      }
      if (!isFatalStartError(error)) {
        // Non-fatal setup issue (audio processing, observers, camera, recording): the call continues.
        vapiWarn("error (non-fatal, ignored)", fields);
        return;
      }
      vapiWarn("error", fields);
      this.starting = false;
      this.setState({ status: VOICE_STATES.ERROR, isConnected: false, error: friendlyVoiceError(error) });
    });
  }

  /** Real SDK mute (setMuted / isMuted). */
  setMuted(muted) {
    if (!this.vapi || !this.state.isConnected) return;
    this.vapi.setMuted(muted);
    this.setState({ isMuted: this.vapi.isMuted() });
  }

  /** Ends the call; the call-end event then finalises once. */
  async stop() {
    if (!this.vapi) return;
    this.stopRequested = true;
    try {
      await this.vapi.stop();
    } catch {
      // The call may already be gone; the caller has a fallback finaliser.
    }
  }

  /** Removes listeners and always releases the SDK call (used on unmount). */
  destroy() {
    this.destroyed = true;
    const vapi = this.vapi;
    this.vapi = null;
    if (vapi) {
      this.handlers.forEach(([event, handler]) => vapi.removeListener(event, handler));
      this.handlers = [];
      // stop() is safe in any state and destroys the underlying Daily call object.
      Promise.resolve().then(() => vapi.stop()).catch(() => {});
    }
    this.listeners.clear();
  }
}

/** Subscribes a component to a client's state. */
export function useVapiState(client) {
  return useSyncExternalStore(
    client ? client.subscribe : noopSubscribe,
    client ? client.getState : initialState,
    // Server snapshot (pre-rendering): no call exists outside the browser.
    initialState
  );
}

function noopSubscribe() {
  return () => {};
}

function initialState() {
  return INITIAL_STATE;
}
