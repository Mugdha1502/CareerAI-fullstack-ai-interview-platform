import api, {
  getToken,
  setToken,
  clearToken,
  toApiError
} from "./api";

import {
  getProfile
} from "./profileService";

/* =====================================================
   CAREERAI AUTH SERVICE

   Registered users: Spring Boot JWT backend
     POST /auth/register, POST /auth/login, GET /auth/me
     Profile + onboarding status: GET /profile/me (see profileService)
     The JWT is stored under "careerai_token".

   Guests: a local-only session (no backend account, no profile API).
===================================================== */

const GUEST_SESSION_KEY = "careerai_guest_session";
const GUEST_ID_KEY = "careerai_guest_id";

// Old frontend-only auth/profile storage, replaced by the backend.
const LEGACY_AUTH_KEYS = [
  "careerai_current_user",      // fake session
  "careerai_registered_users",  // fake user database
  "careerai_local_profile"      // interim local profile store (now in MySQL)
];


function readJSON(key, fallback) {
  try {
    const value = localStorage.getItem(key);
    return value ? JSON.parse(value) : fallback;
  } catch {
    return fallback;
  }
}

function writeJSON(key, value) {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // Storage unavailable — data lasts for this page only.
  }
}

function createGuestId() {
  return `guest_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
}


/** Combines the account (/auth/me) and profile (/profile/me) into the app's user shape. */
function toAppUser(account, profileResult) {
  return {
    id: account.id ?? account.userId,
    name: account.name,
    email: account.email,
    role: account.role,
    plan: account.plan,
    accountType: "REGISTERED",
    profile: profileResult?.profile ?? null,
    onboardingCompleted: Boolean(account.onboardingCompleted)
  };
}


export const authService = {

  getToken,

  /** Removes old frontend-only auth/profile keys now stored by the backend. */
  cleanupLegacyAuth() {
    try {
      LEGACY_AUTH_KEYS.forEach((key) => localStorage.removeItem(key));
    } catch {
      // ignore
    }
  },


  /* ---------- Backend authentication ---------- */

  async register({ name, email, password, confirmPassword }) {
    const { data } = await api.post("/auth/register", { name, email, password, confirmPassword });

    if (data?.token) {
      setToken(data.token);
    }

    this.clearGuestSession();
    return data;
  },

  async login({ email, password }) {
    const { data } = await api.post("/auth/login", { email, password });

    setToken(data.token);
    this.clearGuestSession();
    return data;
  },

  /**
   * Validates the stored JWT (GET /auth/me) and loads the saved profile.
   * Auth errors propagate; if only the profile request fails for another
   * reason, the user is still returned without profile data.
   */
  async getCurrentUser() {
    const { data: account } = await api.get("/auth/me");

    let profileResult = null;

    try {
      profileResult = await getProfile();
    } catch (error) {
      const { status } = toApiError(error);

      if (status === 401 || status === 403) {
        throw error;
      }
    }

    return toAppUser(account, profileResult);
  },

  logout() {
    clearToken();
    this.clearGuestSession();
  },


  /* ---------- Guest mode (local only) ---------- */

  getGuestSession() {
    return readJSON(GUEST_SESSION_KEY, null);
  },

  continueAsGuest() {
    let guestId;

    try {
      guestId = localStorage.getItem(GUEST_ID_KEY);

      if (!guestId) {
        guestId = createGuestId();
        localStorage.setItem(GUEST_ID_KEY, guestId);
      }
    } catch {
      guestId = createGuestId();
    }

    const guest = {
      id: guestId,
      name: "Guest",
      email: null,
      role: "GUEST",
      plan: "GUEST",
      accountType: "GUEST",
      onboardingCompleted: true,
      profile: { targetRole: "Exploring Careers" }
    };

    clearToken();
    writeJSON(GUEST_SESSION_KEY, guest);
    return guest;
  },

  clearGuestSession() {
    try {
      localStorage.removeItem(GUEST_SESSION_KEY);
    } catch {
      // ignore
    }
  },

  /** Guests have no backend profile, so their profile lives in the local guest session. */
  saveGuestProfile(guest, updates) {
    const updated = { ...guest, ...updates };
    writeJSON(GUEST_SESSION_KEY, updated);
    return updated;
  }
};
