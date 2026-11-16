import { CAMERA_STATUS, cameraConstraints, classifyMediaError, openCameraStream } from "./candidateCamera";

/* =====================================================
   DEVICE CHECK — real browser checks for voice interviews.
   Asks for microphone permission (required) and camera permission (optional),
   counts audio inputs and always stops the microphone test tracks afterwards.
===================================================== */

/** Result statuses shown on the device check page. */
export const MIC_STATUS = {
  READY: "READY",
  DENIED: "DENIED",
  NOT_FOUND: "NOT_FOUND",
  UNSUPPORTED: "UNSUPPORTED",
  ERROR: "ERROR"
};

/**
 * @param {MediaDevices | undefined} mediaDevices defaults to navigator.mediaDevices (injectable for tests)
 * @returns {Promise<{ status: string, deviceCount: number, label: string | null }>}
 */
export async function checkMicrophone(mediaDevices = typeof navigator !== "undefined" ? navigator.mediaDevices : undefined) {
  if (!mediaDevices || typeof mediaDevices.getUserMedia !== "function") {
    return { status: MIC_STATUS.UNSUPPORTED, deviceCount: 0, label: null };
  }

  let stream;
  try {
    stream = await mediaDevices.getUserMedia({ audio: true });
  } catch (error) {
    const name = error?.name || "";
    if (name === "NotAllowedError" || name === "SecurityError" || name === "PermissionDeniedError") {
      return { status: MIC_STATUS.DENIED, deviceCount: 0, label: null };
    }
    if (name === "NotFoundError" || name === "DevicesNotFoundError" || name === "OverconstrainedError") {
      return { status: MIC_STATUS.NOT_FOUND, deviceCount: 0, label: null };
    }
    return { status: MIC_STATUS.ERROR, deviceCount: 0, label: null };
  }

  try {
    const label = stream.getAudioTracks()[0]?.label || null;
    let deviceCount = 1;
    if (typeof mediaDevices.enumerateDevices === "function") {
      const devices = await mediaDevices.enumerateDevices();
      deviceCount = devices.filter((device) => device.kind === "audioinput").length;
    }
    return deviceCount > 0
      ? { status: MIC_STATUS.READY, deviceCount, label }
      : { status: MIC_STATUS.NOT_FOUND, deviceCount: 0, label: null };
  } finally {
    // Release the microphone immediately; the voice call opens its own stream.
    stream.getTracks().forEach((track) => track.stop());
  }
}


/* ---------- Camera + microphone (device check page) ---------- */

/**
 * Checks the microphone (required) and the camera (optional) with one permission prompt when
 * possible: getUserMedia({ audio, video }). The microphone track is stopped at once — the voice
 * call opens its own. The camera stream is returned for the local preview; the caller must stop it.
 * If the combined request fails, each device is checked on its own so a denied or missing camera
 * never hides a working microphone.
 *
 * @returns {Promise<{ mic: object, camera: { status: string, stream: MediaStream | null } }>}
 */
export async function checkDevices(mediaDevices = typeof navigator !== "undefined" ? navigator.mediaDevices : undefined, cameraDeviceId = null) {
  if (!mediaDevices || typeof mediaDevices.getUserMedia !== "function") {
    return {
      mic: { status: MIC_STATUS.UNSUPPORTED, deviceCount: 0, label: null },
      camera: { status: CAMERA_STATUS.UNSUPPORTED, stream: null }
    };
  }

  let combined;
  try {
    combined = await mediaDevices.getUserMedia({ audio: true, video: cameraConstraints(cameraDeviceId).video });
  } catch {
    combined = null;
  }

  if (combined) {
    const audioTracks = combined.getAudioTracks();
    const label = audioTracks[0]?.label || null;
    audioTracks.forEach((track) => track.stop());
    const cameraStream = new MediaStream(combined.getVideoTracks());
    let deviceCount = 1;
    if (typeof mediaDevices.enumerateDevices === "function") {
      try {
        const devices = await mediaDevices.enumerateDevices();
        deviceCount = Math.max(1, devices.filter((device) => device.kind === "audioinput").length);
      } catch {
        // keep the default
      }
    }
    return {
      mic: { status: MIC_STATUS.READY, deviceCount, label },
      camera: { status: CAMERA_STATUS.ON, stream: cameraStream }
    };
  }

  const mic = await checkMicrophone(mediaDevices);
  try {
    const stream = await openCameraStream(mediaDevices, cameraDeviceId);
    return { mic, camera: { status: CAMERA_STATUS.ON, stream } };
  } catch (error) {
    return { mic, camera: { status: classifyMediaError(error), stream: null } };
  }
}
