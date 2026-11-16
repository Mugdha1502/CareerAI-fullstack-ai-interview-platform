import { useSyncExternalStore } from "react";
import * as SimliSdk from "simli-client";
import api from "./api";

/* =====================================================
   SIMLI AVATAR SERVICE — the only place that talks to the simli-client SDK.

   Simli is ONLY the visual layer of a voice interview:
   - Vapi keeps the conversation, the questions, the voice, turn taking and the transcript.
   - Vapi's assistant audio track (from the SDK's public `audio` event) is streamed to Simli
     as 16 kHz mono PCM16, and Simli returns a lip-synced video of the configured face.
   - ONE audible voice: Vapi's own playback stays the voice the candidate hears. The audio
     Simli sends back with its video is forced silent (muted + volume 0, re-applied whenever
     LiveKit's attach() unmutes the element).
   - The permanent SIMLI_API_KEY never reaches the browser: the backend exchanges it for a
     short-lived session token (POST /api/avatar/simli/session).
   - Any avatar failure only switches the page back to the built-in interviewer; the Vapi
     call, transcript and report are never touched.
   Logs use the [AVATAR] prefix and never contain the session token.
===================================================== */

// simli-client is CommonJS; depending on the bundler's interop the exports may sit on `default`.
const sdk = SimliSdk.SimliClient ? SimliSdk : SimliSdk.default || {};
const SimliClient = sdk.SimliClient;
// ERROR level: the SDK's INFO/DEBUG output is noisy and not needed in the console.
const SDK_LOG_LEVEL = sdk.LogLevel ? sdk.LogLevel.ERROR : 2;

export const AVATAR_STATES = {
  IDLE: "IDLE",
  CONNECTING: "CONNECTING",
  READY: "READY",
  SPEAKING: "SPEAKING",
  ERROR: "ERROR",
  ENDED: "ENDED"
};

export const AVATAR_UNAVAILABLE_MESSAGE = "Avatar temporarily unavailable";

/** Simli's audio input: PCM16, 16 kHz, mono (simli-client sendAudioData / its own worklet). */
export const PCM_SAMPLE_RATE = 16000;
/** Samples per chunk sent to Simli — simli-client's default audioBufferSize (3000 samples = 6000 bytes). */
export const PCM_CHUNK_SAMPLES = 3000;

// The SDK retries a failed start internally with backoff; give up on the avatar after this.
const CONNECT_TIMEOUT_MS = 30000;
// Consecutive failed audio sends before the avatar is treated as broken (~4.5 s of audio).
const MAX_SEND_FAILURES = 24;

/** How long the page waits for the avatar's first frame before starting the Vapi call anyway. */
export const AVATAR_STARTUP_WAIT_MS = 7000;

// Backend codes after which "Retry Avatar" cannot help.
const NO_RETRY_CODES = new Set(["AVATAR_NOT_CONFIGURED", "AVATAR_LIMIT_REACHED"]);

/*
 * Interviews whose initial avatar session was already requested in this tab. Module level, so it
 * survives rerenders, StrictMode and remounts of the page; a page reload loses it, and the backend
 * then refuses a second "initial" request for the same interview (AVATAR_ALREADY_STARTED).
 */
const initialSessionClaims = new Set();


/* ---------- Backend session ---------- */

/**
 * Short-lived Simli session token for one of the caller's own voice interviews.
 * reason: "initial" (once per interview) or "retry" (explicit Retry Avatar); attempt: 1, 2, 3.
 */
export async function requestSimliSession(interviewId, { attempt = 1, reason = "initial" } = {}) {
  const { data } = await api.post("/avatar/simli/session", { interviewId, attempt, reason });
  return data;
}


/* ---------- Safe diagnostics ---------- */

const SECRET_PATTERN = /(bearer\s+[\w.-]+|eyJ[\w-]+\.[\w-]+\.[\w-]+|session_token=[^&\s]+)/gi;

function safeDetail(error) {
  if (error == null) return {};
  const status = error.response?.status;
  const code = error.response?.data?.code;
  const message = typeof error === "string" ? error : error.message || String(error);
  return Object.fromEntries(Object.entries({
    status,
    code,
    message: String(message).replace(SECRET_PATTERN, "[redacted]").slice(0, 200)
  }).filter(([, value]) => value !== undefined && value !== ""));
}

function avatarLog(event, fields) {
  if (fields) console.info(`[AVATAR] ${event}`, fields);
  else console.info(`[AVATAR] ${event}`);
}

function avatarWarn(event, fields) {
  console.warn(`[AVATAR] ${event}`, fields);
}


/* ---------- One audible voice ---------- */

/**
 * Keeps a media element silent. Simli's LiveKit transport calls track.attach(), which sets
 * `muted = false` whenever an audio track is attached; volume 0 keeps it inaudible anyway and
 * the listener re-mutes it. Returns a function that removes the listeners.
 */
export function silenceMediaElement(element) {
  if (!element) return () => {};
  const enforce = () => {
    if (!element.muted) element.muted = true;
    if (element.volume !== 0) element.volume = 0;
  };
  enforce();
  element.addEventListener("volumechange", enforce);
  element.addEventListener("play", enforce);
  return () => {
    element.removeEventListener("volumechange", enforce);
    element.removeEventListener("play", enforce);
  };
}


/* ---------- Vapi assistant audio → Simli (PCM16 16 kHz mono) ---------- */

const WORKLET_NAME = "careerai-pcm16";

// Float32 samples (already resampled to 16 kHz by the AudioContext) → Int16 chunks.
const WORKLET_SOURCE = `
class CareerAiPcm16 extends AudioWorkletProcessor {
  constructor(options) {
    super();
    this.size = options.processorOptions.chunkSamples;
    this.buffer = new Int16Array(this.size);
    this.index = 0;
  }
  process(inputs) {
    const channel = inputs[0] && inputs[0][0];
    if (channel) {
      for (let i = 0; i < channel.length; i++) {
        const sample = Math.max(-1, Math.min(1, channel[i]));
        this.buffer[this.index++] = sample < 0 ? sample * 0x8000 : sample * 0x7fff;
        if (this.index === this.size) {
          this.port.postMessage(this.buffer.buffer, [this.buffer.buffer]);
          this.buffer = new Int16Array(this.size);
          this.index = 0;
        }
      }
    }
    return true;
  }
}
registerProcessor("${WORKLET_NAME}", CareerAiPcm16);
`;

function defaultAudioContext() {
  const AudioContextClass = window.AudioContext || window.webkitAudioContext;
  return new AudioContextClass({ sampleRate: PCM_SAMPLE_RATE });
}

/**
 * Streams one MediaStreamTrack into Simli. One AudioContext and one worklet per avatar session;
 * a replaced track only swaps the source node, so there is never a second processor.
 *
 * The track is Vapi's own remote track, used as is (not cloned): Chrome only delivers remote
 * WebRTC audio to Web Audio while the same track is playing in a media element, which Vapi's
 * player does. Nothing is connected to the destination, so this adds no playback, and the track
 * is never stopped here (it belongs to Vapi).
 */
export class AvatarAudioFeed {
  constructor({ send, createAudioContext = defaultAudioContext, chunkSamples = PCM_CHUNK_SAMPLES }) {
    this.send = send;
    this.createAudioContext = createAudioContext;
    this.chunkSamples = chunkSamples;
    this.context = null;
    this.node = null;
    this.source = null;
    this.track = null;
    this.graph = null;
    this.sequence = 0;
    this.closed = false;
  }

  ensureGraph() {
    if (!this.graph) {
      this.graph = (async () => {
        this.context = this.createAudioContext();
        const url = URL.createObjectURL(new Blob([WORKLET_SOURCE], { type: "application/javascript" }));
        try {
          await this.context.audioWorklet.addModule(url);
        } finally {
          URL.revokeObjectURL(url);
        }
        if (this.closed) return;
        this.node = new AudioWorkletNode(this.context, WORKLET_NAME, {
          numberOfInputs: 1,
          numberOfOutputs: 0,
          channelCount: 1,
          channelCountMode: "explicit",
          channelInterpretation: "speakers",
          processorOptions: { chunkSamples: this.chunkSamples }
        });
        this.node.port.onmessage = (event) => this.send(new Uint8Array(event.data));
        if (this.context.state === "suspended") {
          await this.context.resume().catch(() => {});
        }
      })();
    }
    return this.graph;
  }

  /** Feeds this track (null stops feeding). The same track twice is a no-op. True when attached. */
  async setTrack(track) {
    if (this.closed || track === this.track) return false;
    const sequence = ++this.sequence;
    this.track = track;
    if (!track || track.readyState === "ended") {
      this.detachSource();
      return false;
    }
    await this.ensureGraph();
    if (this.closed || sequence !== this.sequence) return false;
    // Replace only the input: the previous source is disconnected, the processor stays.
    this.detachSource();
    this.source = this.context.createMediaStreamSource(new MediaStream([track]));
    this.source.connect(this.node);
    return true;
  }

  detachSource() {
    try {
      this.source?.disconnect();
    } catch {
      // already disconnected
    }
    this.source = null;
  }

  close() {
    if (this.closed) return;
    this.closed = true;
    this.detachSource();
    if (this.node) {
      this.node.port.onmessage = null;
      try {
        this.node.disconnect();
      } catch {
        // already disconnected
      }
    }
    this.node = null;
    this.track = null;
    const context = this.context;
    this.context = null;
    if (context && context.state !== "closed") context.close().catch(() => {});
  }
}


/* ---------- Avatar client ---------- */

const INITIAL_STATE = {
  status: AVATAR_STATES.IDLE,
  error: null,
  canRetry: false,
  attempt: 0
};

/**
 * One Simli avatar for one voice interview.
 * - connect() opens the ONE initial session for the interview (attempt 1). Every later call —
 *   rerenders, StrictMode, a remounted page — reuses it and never requests another session.
 * - retry() is the only other way to open a session (explicit Retry Avatar after a failure).
 * - setAudioTrack() hands over Vapi's assistant track; it is stored until the session is ready,
 *   then attached to the existing session (a replacement track only swaps the input).
 * - whenSettled() resolves on the real readiness signal (first frame) or failure, or a timeout.
 * - stop() ends the session (interview over); destroy() also drops subscribers (unmount).
 * Dependencies are injectable for tests.
 */
export class SimliAvatarClient {
  constructor({
    interviewId = null,
    requestSession = requestSimliSession,
    SimliClientClass = SimliClient,
    createAudioContext = defaultAudioContext,
    logLevel = SDK_LOG_LEVEL,
    connectTimeoutMs = CONNECT_TIMEOUT_MS
  } = {}) {
    this.interviewId = interviewId;
    this.requestSession = requestSession;
    this.SimliClientClass = SimliClientClass;
    this.createAudioContext = createAudioContext;
    this.logLevel = logLevel;
    this.connectTimeoutMs = connectTimeoutMs;

    this.state = INITIAL_STATE;
    this.listeners = new Set();
    this.elements = null;
    this.simli = null;
    this.handlers = [];
    this.feed = null;
    this.track = null;
    this.unsilencers = [];
    this.connectRequested = false;
    this.opening = false;
    this.sessionSequence = 0;
    this.sendFailures = 0;
    this.destroyed = false;

    this.subscribe = this.subscribe.bind(this);
    this.getState = this.getState.bind(this);
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

  isLive() {
    const { status } = this.state;
    return this.opening || status === AVATAR_STATES.CONNECTING || status === AVATAR_STATES.READY || status === AVATAR_STATES.SPEAKING;
  }

  /** Opens the interview's initial avatar session, at most once; the elements are the page's <video> and (silenced) <audio>. */
  async connect({ videoElement, audioElement }) {
    if (this.destroyed || this.state.status === AVATAR_STATES.ENDED) return;
    if (this.connectRequested) {
      avatarLog("existing connect reused", { interviewId: this.interviewId, state: this.state.status, attempt: this.state.attempt });
      return;
    }
    this.connectRequested = true;
    this.attachElements({ videoElement, audioElement });

    if (initialSessionClaims.has(this.interviewId)) {
      // This page was remounted for an interview whose initial session was already used.
      avatarLog("initial session already used", { interviewId: this.interviewId, state: "waiting for Retry Avatar" });
      this.fail(true);
      return;
    }
    initialSessionClaims.add(this.interviewId);
    avatarLog("initial connect requested", { interviewId: this.interviewId, attempt: 1 });
    await this.open("initial");
  }

  /** Reconnects only the avatar after a failure (new session token, new SDK client — the SDK cannot be reused). */
  async retry() {
    avatarLog("retry clicked", { interviewId: this.interviewId, state: this.state.status, attempt: this.state.attempt + 1 });
    if (this.destroyed || this.isLive() || !this.elements || this.state.status !== AVATAR_STATES.ERROR || !this.state.canRetry) return;
    await this.teardownSession();
    await this.open("retry");
  }

  /**
   * The page's media elements. Called after every render: if the elements were remounted while a
   * session is live, the video stream moves to the new <video> and the new <audio> is silenced too.
   */
  attachElements({ videoElement, audioElement }) {
    if (!videoElement || !audioElement) return;
    const previous = this.elements;
    this.elements = { videoElement, audioElement };
    if (!previous || !this.simli) return;
    if (previous.videoElement !== videoElement && previous.videoElement.srcObject) {
      videoElement.srcObject = previous.videoElement.srcObject;
    }
    if (previous.audioElement !== audioElement) {
      // The SDK keeps the old element; keep it silenced as well as the new one.
      this.unsilencers.push(silenceMediaElement(audioElement));
    }
  }

  /**
   * Resolves with the avatar status once it is READY (first frame), failed or ended — or with
   * "TIMEOUT" after timeoutMs. Used to start the Vapi call with the avatar already visible
   * without ever making the interview wait for it indefinitely.
   */
  whenSettled(timeoutMs) {
    const settled = () => [AVATAR_STATES.READY, AVATAR_STATES.SPEAKING, AVATAR_STATES.ERROR, AVATAR_STATES.ENDED].includes(this.state.status);
    if (settled()) return Promise.resolve(this.state.status);
    return new Promise((resolve) => {
      const unsubscribe = this.subscribe(() => {
        if (!settled()) return;
        clearTimeout(timer);
        unsubscribe();
        resolve(this.state.status);
      });
      const timer = setTimeout(() => {
        unsubscribe();
        resolve("TIMEOUT");
      }, timeoutMs);
    });
  }

  /** Vapi's assistant audio track (or null). Stored until the session is ready, then attached once. */
  setAudioTrack(track) {
    const next = track || null;
    if (next === this.track) return;
    this.track = next;
    if (!this.feed) {
      if (next) avatarLog("audio stored", { interviewId: this.interviewId, state: this.state.status });
      return;
    }
    this.attachAudio();
  }

  attachAudio() {
    const feed = this.feed;
    feed.setTrack(this.track)
      .then((attached) => {
        if (attached && feed === this.feed) avatarLog("audio attached", { interviewId: this.interviewId, attempt: this.state.attempt });
      })
      .catch((error) => this.failAudio(error));
  }

  async open(reason) {
    const sequence = ++this.sessionSequence;
    const attempt = this.state.attempt + 1;
    this.opening = true;
    this.sendFailures = 0;
    this.setState({ status: AVATAR_STATES.CONNECTING, error: null, canRetry: false, attempt });
    avatarLog("session request", { interviewId: this.interviewId, attempt, reason });

    try {
      let session;
      try {
        session = await this.requestSession(this.interviewId, { attempt, reason });
      } catch (error) {
        const status = error?.response?.status;
        const code = error?.response?.data?.code;
        avatarWarn("session request failed", { interviewId: this.interviewId, attempt, ...safeDetail(error) });
        // AVATAR_ALREADY_STARTED (a reloaded page): the initial session was used; Retry Avatar may open another.
        const canRetry = code === "AVATAR_ALREADY_STARTED"
          || (!NO_RETRY_CODES.has(code) && status !== 409 && status !== 404);
        this.fail(canRetry);
        return;
      }
      if (this.isStale(sequence)) return;
      if (!session?.sessionToken) {
        avatarWarn("session request failed", { message: "no session token" });
        this.fail(true);
        return;
      }

      if (typeof this.SimliClientClass !== "function") {
        throw new TypeError("Avatar SDK failed to load (constructor unavailable).");
      }
      const { videoElement, audioElement } = this.elements;
      this.unsilencers.push(silenceMediaElement(audioElement));
      // LiveKit transport (the SDK default): no ICE servers needed.
      const simli = new this.SimliClientClass(session.sessionToken, videoElement, audioElement, null, this.logLevel, "livekit");
      this.simli = simli;
      this.bind(simli);

      let timer;
      const timeout = new Promise((_, reject) => {
        timer = setTimeout(() => reject(new Error("Avatar connection timed out")), this.connectTimeoutMs);
      });
      try {
        await Promise.race([simli.start(), timeout]);
      } finally {
        clearTimeout(timer);
      }
      if (this.isStale(sequence) || this.simli !== simli) return;

      this.feed = new AvatarAudioFeed({ send: (bytes) => this.sendAudio(simli, bytes), createAudioContext: this.createAudioContext });
      if (this.state.status === AVATAR_STATES.CONNECTING) this.setState({ status: AVATAR_STATES.READY });
      avatarLog("ready", { interviewId: this.interviewId, attempt, audioStored: Boolean(this.track) });
      // Audio that arrived before the avatar was ready is attached now, once.
      if (this.track) this.attachAudio();
    } catch (error) {
      if (this.isStale(sequence)) return;
      avatarWarn("connect failed", { interviewId: this.interviewId, attempt: this.state.attempt, ...safeDetail(error) });
      await this.teardownSession();
      this.fail(true);
    } finally {
      if (sequence === this.sessionSequence) this.opening = false;
    }
  }

  isStale(sequence) {
    return this.destroyed || sequence !== this.sessionSequence;
  }

  bind(simli) {
    const on = (event, handler) => {
      simli.on(event, handler);
      this.handlers.push([event, handler]);
    };
    // First video frame rendered.
    on("start", () => {
      if (this.simli === simli && this.state.status === AVATAR_STATES.CONNECTING) this.setState({ status: AVATAR_STATES.READY });
    });
    // Simli is rendering speech (driven by Vapi's audio) / back to idle.
    on("speaking", () => {
      if (this.simli === simli && this.state.status === AVATAR_STATES.READY) this.setState({ status: AVATAR_STATES.SPEAKING });
    });
    on("silent", () => {
      if (this.simli === simli && this.state.status === AVATAR_STATES.SPEAKING) this.setState({ status: AVATAR_STATES.READY });
    });
    // After the connection is live: server error, or the server ended the session (time/idle cap).
    on("error", (detail) => this.handleSessionLost(simli, "error", detail));
    on("stop", () => this.handleSessionLost(simli, "stop"));
    on("startup_error", (detail) => avatarWarn("startup error (SDK retries)", safeDetail(detail)));
  }

  handleSessionLost(simli, event, detail) {
    if (this.simli !== simli || this.destroyed || this.state.status === AVATAR_STATES.ENDED) return;
    if (this.opening) return; // start() rejects and is handled in open()
    // No automatic restart: the fallback shows and only Retry Avatar opens a new session.
    avatarWarn(`session ${event}`, { interviewId: this.interviewId, attempt: this.state.attempt, ...safeDetail(detail) });
    this.teardownSession().finally(() => this.fail(true));
  }

  sendAudio(simli, bytes) {
    if (this.simli !== simli) return;
    try {
      simli.sendAudioData(bytes);
      this.sendFailures = 0;
    } catch (error) {
      this.sendFailures += 1;
      if (this.sendFailures === 1) avatarWarn("audio send failed", safeDetail(error));
      if (this.sendFailures >= MAX_SEND_FAILURES) this.failAudio(error);
    }
  }

  failAudio(error) {
    if (this.destroyed || !this.simli) return;
    avatarWarn("audio forwarding stopped", safeDetail(error));
    this.teardownSession().finally(() => this.fail(true));
  }

  fail(canRetry) {
    if (this.destroyed || this.state.status === AVATAR_STATES.ENDED) return;
    this.setState({ status: AVATAR_STATES.ERROR, error: AVATAR_UNAVAILABLE_MESSAGE, canRetry });
  }

  /** Releases the current Simli session: audio graph, listeners, connection, media. */
  async teardownSession() {
    const simli = this.simli;
    this.simli = null;
    this.feed?.close();
    this.feed = null;
    this.unsilencers.forEach((remove) => remove());
    this.unsilencers = [];
    if (!simli) return;

    this.handlers.forEach(([event, handler]) => {
      try {
        simli.off(event, handler);
      } catch {
        // not registered
      }
    });
    this.handlers = [];
    try {
      await simli.stop();
    } catch (error) {
      avatarWarn("stop failed", safeDetail(error));
    }
    // simli-client 3.2.0 creates an AudioContext in its constructor that stop() never closes.
    const sdkContext = simli.audioContext;
    if (sdkContext && typeof sdkContext.close === "function" && sdkContext.state !== "closed") {
      sdkContext.close().catch(() => {});
    }
    const video = this.elements?.videoElement;
    if (video) video.srcObject = null;
    const audio = this.elements?.audioElement;
    if (audio) audio.srcObject = null;
  }

  /** Ends the avatar for good (interview finished). Safe to call more than once. */
  async stop() {
    if (this.state.status === AVATAR_STATES.ENDED) return;
    this.sessionSequence += 1;
    this.opening = false;
    const hadSession = Boolean(this.simli);
    this.setState({ status: AVATAR_STATES.ENDED, canRetry: false });
    await this.teardownSession();
    if (hadSession) avatarLog("stopped", { interviewId: this.interviewId });
  }

  /** Unmount: stops the session and drops subscribers. */
  destroy() {
    if (this.destroyed) return;
    const pending = this.stop();
    this.destroyed = true;
    this.listeners.clear();
    return pending;
  }
}

/** Subscribes a component to an avatar client's state. */
export function useAvatarState(client) {
  return useSyncExternalStore(
    client ? client.subscribe : noopSubscribe,
    client ? client.getState : initialState,
    initialState
  );
}

function noopSubscribe() {
  return () => {};
}

function initialState() {
  return INITIAL_STATE;
}
