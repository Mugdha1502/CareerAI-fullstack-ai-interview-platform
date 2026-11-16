import { useSyncExternalStore } from "react";

/* =====================================================
   CANDIDATE CAMERA — local self-view only.

   - Opens the webcam with getUserMedia({ video, audio: false }): never a microphone track
     (Vapi owns the microphone during the interview).
   - The stream is only attached to a local, muted <video>. It is never sent to Vapi or Simli,
     never recorded, uploaded or analysed.
   - Only tracks opened here are ever stopped here.
===================================================== */

export const CAMERA_STATUS = {
  OFF: "OFF",
  STARTING: "STARTING",
  ON: "ON",
  DENIED: "DENIED",
  NOT_FOUND: "NOT_FOUND",
  BUSY: "BUSY",
  UNSUPPORTED: "UNSUPPORTED",
  ERROR: "ERROR"
};

export const CAMERA_MESSAGES = {
  [CAMERA_STATUS.DENIED]: "Camera permission denied. Continue with voice only.",
  [CAMERA_STATUS.NOT_FOUND]: "No camera detected. Continue with voice only.",
  [CAMERA_STATUS.BUSY]: "Camera is currently unavailable.",
  [CAMERA_STATUS.UNSUPPORTED]: "This browser cannot open a camera. Continue with voice only.",
  [CAMERA_STATUS.ERROR]: "The camera could not be opened. Continue with voice only."
};

export const CAMERA_STOPPED_MESSAGE = "The camera stopped unexpectedly.";

function defaultMediaDevices() {
  return typeof navigator !== "undefined" ? navigator.mediaDevices : undefined;
}

/** Maps a getUserMedia error to a CAMERA_STATUS (also used for the microphone's DENIED/NOT_FOUND split). */
export function classifyMediaError(error) {
  const name = error?.name || "";
  if (name === "NotAllowedError" || name === "SecurityError" || name === "PermissionDeniedError") return CAMERA_STATUS.DENIED;
  if (name === "NotFoundError" || name === "DevicesNotFoundError" || name === "OverconstrainedError") return CAMERA_STATUS.NOT_FOUND;
  if (name === "NotReadableError" || name === "TrackStartError" || name === "AbortError") return CAMERA_STATUS.BUSY;
  return CAMERA_STATUS.ERROR;
}

/** Video constraints for an optional device id (falls back to any camera when it is gone). */
export function cameraConstraints(deviceId) {
  return { video: deviceId ? { deviceId: { exact: deviceId } } : true, audio: false };
}

/** Lists cameras ({ deviceId, label }); labels are only filled once camera permission was granted. */
export async function listCameras(mediaDevices = defaultMediaDevices()) {
  if (!mediaDevices || typeof mediaDevices.enumerateDevices !== "function") return [];
  try {
    const devices = await mediaDevices.enumerateDevices();
    return devices
      .filter((device) => device.kind === "videoinput")
      .map((device, index) => ({ deviceId: device.deviceId, label: device.label || `Camera ${index + 1}` }));
  } catch {
    return [];
  }
}

/** Opens a camera-only stream; an unknown device id falls back to the default camera. */
export async function openCameraStream(mediaDevices, deviceId) {
  try {
    return await mediaDevices.getUserMedia(cameraConstraints(deviceId));
  } catch (error) {
    if (deviceId && (error?.name === "OverconstrainedError" || error?.name === "NotFoundError")) {
      return mediaDevices.getUserMedia(cameraConstraints(null));
    }
    throw error;
  }
}

export function stopTracks(stream) {
  stream?.getTracks().forEach((track) => track.stop());
}


/* ---------- Controller (one camera stream per page) ---------- */

const INITIAL_STATE = { status: CAMERA_STATUS.OFF, stream: null, error: null };

/**
 * Owns the candidate's local camera stream for one page.
 * - turnOn() opens ONE stream; calls while starting or on are ignored (StrictMode / double clicks).
 * - turnOff() stops only the camera's own tracks.
 * - A track that ends on its own (unplugged, taken by the OS) falls back to OFF with a notice.
 * mediaDevices is injectable for tests.
 */
export class CandidateCameraController {
  constructor({ mediaDevices = defaultMediaDevices(), deviceId = null } = {}) {
    this.mediaDevices = mediaDevices;
    this.deviceId = deviceId;
    this.state = INITIAL_STATE;
    this.listeners = new Set();
    this.sequence = 0;
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

  isOn() {
    return this.state.status === CAMERA_STATUS.ON || this.state.status === CAMERA_STATUS.STARTING;
  }

  async turnOn() {
    if (this.destroyed || this.isOn()) return;
    if (!this.mediaDevices || typeof this.mediaDevices.getUserMedia !== "function") {
      this.setState({ status: CAMERA_STATUS.UNSUPPORTED, stream: null, error: CAMERA_MESSAGES[CAMERA_STATUS.UNSUPPORTED] });
      return;
    }
    const sequence = ++this.sequence;
    this.setState({ status: CAMERA_STATUS.STARTING, stream: null, error: null });

    let stream;
    try {
      stream = await openCameraStream(this.mediaDevices, this.deviceId);
    } catch (error) {
      if (sequence !== this.sequence || this.destroyed) return;
      const status = classifyMediaError(error);
      this.setState({ status, stream: null, error: CAMERA_MESSAGES[status] });
      return;
    }
    // Turned off or left while the browser was opening the camera: release it at once.
    if (sequence !== this.sequence || this.destroyed) {
      stopTracks(stream);
      return;
    }
    stream.getVideoTracks().forEach((track) => {
      track.addEventListener("ended", () => this.handleEnded(stream));
    });
    this.setState({ status: CAMERA_STATUS.ON, stream, error: null });
  }

  handleEnded(stream) {
    if (this.state.stream !== stream || this.destroyed) return;
    stopTracks(stream);
    this.setState({ status: CAMERA_STATUS.OFF, stream: null, error: CAMERA_STOPPED_MESSAGE });
  }

  turnOff() {
    this.sequence += 1;
    const stream = this.state.stream;
    stopTracks(stream);
    if (this.state.status !== CAMERA_STATUS.OFF || stream) {
      this.setState({ status: CAMERA_STATUS.OFF, stream: null, error: null });
    }
  }

  /** Page left / interview over: stops the camera and drops subscribers. */
  destroy() {
    if (this.destroyed) return;
    this.turnOff();
    this.destroyed = true;
    this.listeners.clear();
  }
}

export function useCameraState(controller) {
  return useSyncExternalStore(
    controller ? controller.subscribe : noopSubscribe,
    controller ? controller.getState : initialState,
    initialState
  );
}

function noopSubscribe() {
  return () => {};
}

function initialState() {
  return INITIAL_STATE;
}
