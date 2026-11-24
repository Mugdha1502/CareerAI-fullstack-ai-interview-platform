import {
  useCallback,
  useEffect,
  useState
} from "react";

import { AuthContext } from "./auth";

import {
  authService
} from "../services/authService";

import {
  clearToken,
  toApiError,
  SESSION_EXPIRED_EVENT
} from "../services/api";

import {
  completeOnboarding as saveOnboarding,
  updateProfile
} from "../services/profileService";


const SESSION_EXPIRED_MESSAGE =
  "Your session has expired. Please log in again.";


// Remove the old mock-auth "database" once, before anything reads it.
authService.cleanupLegacyAuth();


export function AuthProvider({
  children
}) {

  // With a stored JWT we must ask the backend who the user is before rendering
  // protected pages. Without one, the only possible session is a local guest.
  const [loading, setLoading] =
    useState(() =>
      Boolean(authService.getToken())
    );

  const [user, setUser] =
    useState(() =>
      authService.getToken()
        ? null
        : authService.getGuestSession()
    );

  // One-off message for the login page (expired session, server unreachable).
  const [sessionMessage, setSessionMessage] =
    useState("");


  // 1–5. On load: validate the stored token with GET /auth/me.
  useEffect(() => {

    if (!authService.getToken()) {
      return undefined;
    }

    let cancelled = false;

    authService
      .getCurrentUser()
      .then((currentUser) => {
        if (!cancelled) {
          setUser(currentUser);
        }
      })
      .catch((error) => {
        if (cancelled) return;

        const { status, message } =
          toApiError(error);

        if (status === 401 || status === 403) {
          // Invalid or expired token.
          clearToken();
          setSessionMessage(SESSION_EXPIRED_MESSAGE);
        } else {
          // Backend unreachable: keep the token so a later reload can restore the session.
          setSessionMessage(message);
        }

        setUser(null);
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };

  }, []);


  // A 401 on any later authenticated request signs the user out.
  useEffect(() => {

    const handleExpired = () => {
      setUser(null);
      setSessionMessage(SESSION_EXPIRED_MESSAGE);
    };

    window.addEventListener(
      SESSION_EXPIRED_EVENT,
      handleExpired
    );

    return () =>
      window.removeEventListener(
        SESSION_EXPIRED_EVENT,
        handleExpired
      );

  }, []);


  /** Calls the backend, stores the JWT, loads /auth/me and updates context. */
  const login = async (
    credentials
  ) => {

    await authService.login(credentials);

    const currentUser =
      await authService.getCurrentUser();

    setUser(currentUser);
    setSessionMessage("");

    return currentUser;
  };


  /**
   * Registers with the backend. When the response includes a JWT the user is
   * signed in immediately; otherwise { requiresLogin: true } is returned.
   */
  const register = async (
    data
  ) => {

    const response =
      await authService.register(data);

    if (!response?.token) {
      return { requiresLogin: true };
    }

    const currentUser =
      await authService.getCurrentUser();

    setUser(currentUser);
    setSessionMessage("");

    return currentUser;
  };


  const continueAsGuest =
    () => {

      const guest =
        authService.continueAsGuest();

      setUser(guest);
      setSessionMessage("");

      return guest;
    };


  const logout = () => {

    authService.logout();

    setUser(null);
  };


  /** Re-reads the account and profile from the backend (GET /auth/me + /profile/me). */
  const refreshUser = async () => {

    if (!authService.getToken()) {
      return user;
    }

    const currentUser =
      await authService.getCurrentUser();

    setUser(currentUser);

    return currentUser;
  };


  /**
   * Saves onboarding answers. Registered users: POST /profile/onboarding, then
   * refresh so onboardingCompleted comes back true from the backend.
   * Guests: stored in the local guest session.
   */
  const completeOnboarding = async (
    profile
  ) => {

    if (user?.accountType === "GUEST") {

      const updated =
        authService.saveGuestProfile(user, {
          profile,
          onboardingCompleted: true
        });

      setUser(updated);

      return updated;
    }

    await saveOnboarding(profile);

    return refreshUser();
  };


  /** Replaces the profile. Registered users: PUT /profile/me. Guests: local session. */
  const saveProfile = async (
    profile
  ) => {

    if (user?.accountType === "GUEST") {

      const updated =
        authService.saveGuestProfile(user, { profile });

      setUser(updated);

      return updated;
    }

    const result =
      await updateProfile(profile);

    const updated = {
      ...user,
      profile: result.profile,
      onboardingCompleted: result.onboardingCompleted
    };

    setUser(updated);

    return updated;
  };


  /**
   * Local-only change to the in-memory user (not persisted for registered users).
   * Use saveProfile() for anything that must reach the backend.
   */
  const updateUser =
    (updates) => {

      const updated =
        user?.accountType === "GUEST"
          ? authService.saveGuestProfile(user, updates)
          : { ...user, ...updates };

      setUser(updated);

      return updated;
    };


  const consumeSessionMessage =
    useCallback(() => {
      setSessionMessage("");
    }, []);


  const isGuest =
    user?.accountType === "GUEST";

  const isAuthenticated =
    Boolean(user) && !isGuest;


  return (
    <AuthContext.Provider
      value={{
        user,
        loading,

        // True only for a backend user validated through /auth/me.
        isAuthenticated,

        // Any usable session: an authenticated user or a local guest.
        hasSession:
          Boolean(user),

        isGuest,

        isRegistered:
          isAuthenticated,

        sessionMessage,
        consumeSessionMessage,

        register,
        login,
        logout,
        continueAsGuest,
        completeOnboarding,
        saveProfile,
        refreshUser,
        updateUser
      }}
    >

      {children}

    </AuthContext.Provider>
  );
}
