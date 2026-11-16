import axios from "axios";

/* =====================================================
   CAREERAI API CLIENT
   Shared Axios instance for the Spring Boot backend.
===================================================== */

/** Backend base URL. Set VITE_API_BASE_URL for deployed builds (public value, not a secret). */
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api";

export const TOKEN_KEY = "careerai_token";

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: { "Content-Type": "application/json" },
  timeout: 15000
});


/* ---------- Token storage (development: localStorage) ---------- */

export function getToken() {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setToken(token) {
  try {
    localStorage.setItem(TOKEN_KEY, token);
  } catch {
    // Storage unavailable — the session lasts for this page only.
  }
}

export function clearToken() {
  try {
    localStorage.removeItem(TOKEN_KEY);
  } catch {
    // ignore
  }
}


/* ---------- Authorization header ---------- */

// Attach the JWT only when one exists; anonymous requests carry no Authorization header.
api.interceptors.request.use((config) => {
  const token = getToken();

  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});


/* ---------- Expired / revoked sessions ---------- */

export const SESSION_EXPIRED_EVENT = "careerai:session-expired";

/** Fired for every 429 PLAN_LIMIT_REACHED so one global dialog can offer the upgrade (detail = payload). */
export const PLAN_LIMIT_EVENT = "careerai:plan-limit";

// A 401 on an authenticated request means the JWT is no longer valid: drop it and
// tell AuthContext. Login/register 401s are wrong credentials, not an expired session.
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const sentToken = Boolean(error?.config?.headers?.Authorization);
    const url = error?.config?.url || "";
    const isCredentialRequest = url.startsWith("/auth/login") || url.startsWith("/auth/register");

    if (error?.response?.status === 401 && sentToken && !isCredentialRequest) {
      clearToken();
      window.dispatchEvent(new CustomEvent(SESSION_EXPIRED_EVENT));
    }

    if (error?.response?.status === 429 && error.response.data?.code === "PLAN_LIMIT_REACHED") {
      window.dispatchEvent(new CustomEvent(PLAN_LIMIT_EVENT, { detail: error.response.data }));
    }

    return Promise.reject(error);
  }
);


/* ---------- Error messages ---------- */

const STATUS_MESSAGES = {
  400: "Please check the details you entered.",
  401: "Your session has expired. Please log in again.",
  403: "You do not have permission to do that.",
  404: "The requested resource was not found.",
  409: "This request conflicts with existing data.",
  500: "Something went wrong on the CareerAI server. Please try again."
};

/**
 * Turns an Axios error into { status, message, fieldErrors }.
 * The backend returns { "message": "...", "errors": { field: "..." } };
 * its message is preferred over the generic status text.
 */
export function toApiError(error) {
  if (!error?.response) {
    return {
      status: 0,
      message: "Unable to connect to CareerAI server. Please check that the backend is running.",
      fieldErrors: {}
    };
  }

  const { status, data } = error.response;

  return {
    status,
    message: data?.message || STATUS_MESSAGES[status] || "Request failed. Please try again.",
    fieldErrors: data?.errors && typeof data.errors === "object" ? data.errors : {}
  };
}

export default api;
