import {
  useState
} from "react";

import {
  Link,
  useLocation,
  useNavigate
} from "react-router-dom";

import {
  FiMail,
  FiLock,
  FiEye,
  FiEyeOff,
  FiArrowRight,
  FiCheckCircle,
  FiLoader
} from "react-icons/fi";

import {
  useAuth
} from "../../context/auth";

import {
  toApiError
} from "../../services/api";


const EMAIL_PATTERN =
  /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;


function Login() {

  const navigate =
    useNavigate();

  const location =
    useLocation();


  const {
    login,
    continueAsGuest,
    sessionMessage,
    consumeSessionMessage
  } = useAuth();


  const [email, setEmail] =
    useState("");

  const [password,
    setPassword] =
    useState("");

  const [showPassword,
    setShowPassword] =
    useState(false);

  const [error,
    setError] =
    useState("");

  const [fieldErrors,
    setFieldErrors] =
    useState({});

  const [submitting,
    setSubmitting] =
    useState(false);


  // Never send the user back to the auth pages after logging in.
  const redirectTarget = () => {

    const from =
      location.state?.from;

    return from &&
      !from.startsWith("/login") &&
      !from.startsWith("/register")
      ? from
      : "/dashboard";
  };


  const validate = () => {

    const errors = {};

    if (!EMAIL_PATTERN.test(email.trim())) {
      errors.email =
        "Please enter a valid email address.";
    }

    if (!password) {
      errors.password =
        "Please enter your password.";
    }

    return errors;
  };


  const handleSubmit =
    async (event) => {

      event.preventDefault();

      if (submitting) {
        return;
      }

      setError("");
      consumeSessionMessage();


      const errors =
        validate();

      setFieldErrors(errors);

      if (Object.keys(errors).length) {
        return;
      }


      setSubmitting(true);

      try {

        const user =
          await login({
            email: email.trim(),
            password
          });


        navigate(
          user.onboardingCompleted
            ? redirectTarget()
            : "/onboarding",
          { replace: true }
        );

      } catch (err) {

        const apiError =
          toApiError(err);

        setFieldErrors(
          apiError.fieldErrors
        );

        setError(
          apiError.status === 401
            ? "Invalid email or password."
            : apiError.message
        );

        setSubmitting(false);
      }

    };


  const handleGuest =
    () => {

      continueAsGuest();

      navigate(
        "/dashboard"
      );

    };


  const bannerMessage =
    error || sessionMessage;


  return (
    <div className="auth-page">

      <section className="auth-info-panel">

        <span className="auth-info-tag">
          WELCOME BACK
        </span>

        <h1>
          Continue building your
          <span>
            {" "}career readiness.
          </span>
        </h1>

        <p>
          Continue your preparation,
          interviews and career journey
          from where you stopped.
        </p>


        <div className="auth-benefits">

          <div>
            <FiCheckCircle />
            Continue your daily plan
          </div>

          <div>
            <FiCheckCircle />
            Maintain your XP and streak
          </div>

          <div>
            <FiCheckCircle />
            Review interview performance
          </div>

          <div>
            <FiCheckCircle />
            Continue career roadmap
          </div>

        </div>

      </section>


      <section className="auth-form-panel">

        <div className="auth-form-card">

          <div className="auth-form-heading">

            <span className="section-tag">
              LOG IN
            </span>

            <h2>
              Welcome back
            </h2>

            <p>
              New to CareerAI?{" "}

              <Link to="/register">
                Create account
              </Link>
            </p>

          </div>


          {bannerMessage && (
            <div
              className="auth-error"
              role="alert"
            >
              {bannerMessage}
            </div>
          )}


          <form
            className="auth-form"
            onSubmit={handleSubmit}
            noValidate
          >

            <div className="auth-field">

              <label htmlFor="login-email">
                Email address
              </label>

              <div className={`auth-input${fieldErrors.email ? " has-error" : ""}`}>

                <FiMail aria-hidden="true" />

                <input
                  id="login-email"
                  type="email"
                  autoComplete="email"
                  placeholder="you@example.com"
                  value={email}
                  disabled={submitting}
                  aria-invalid={Boolean(fieldErrors.email)}
                  aria-describedby={fieldErrors.email ? "login-email-error" : undefined}
                  onChange={(event) =>
                    setEmail(
                      event.target.value
                    )
                  }
                />

              </div>

              {fieldErrors.email && (
                <span
                  id="login-email-error"
                  className="auth-field-error"
                >
                  {fieldErrors.email}
                </span>
              )}

            </div>


            <div className="auth-field">

              <label htmlFor="login-password">
                Password
              </label>

              <div className={`auth-input${fieldErrors.password ? " has-error" : ""}`}>

                <FiLock aria-hidden="true" />

                <input
                  id="login-password"
                  type={
                    showPassword
                      ? "text"
                      : "password"
                  }
                  autoComplete="current-password"
                  placeholder="Enter password"
                  value={password}
                  disabled={submitting}
                  aria-invalid={Boolean(fieldErrors.password)}
                  aria-describedby={fieldErrors.password ? "login-password-error" : undefined}
                  onChange={(event) =>
                    setPassword(
                      event.target.value
                    )
                  }
                />

                <button
                  type="button"
                  className="password-toggle"
                  aria-label={showPassword ? "Hide password" : "Show password"}
                  onClick={() =>
                    setShowPassword(
                      !showPassword
                    )
                  }
                >
                  {showPassword
                    ? <FiEyeOff />
                    : <FiEye />}
                </button>

              </div>

              {fieldErrors.password && (
                <span
                  id="login-password-error"
                  className="auth-field-error"
                >
                  {fieldErrors.password}
                </span>
              )}

            </div>


            <button
              className="auth-submit"
              type="submit"
              disabled={submitting}
              aria-busy={submitting}
            >
              {submitting ? (
                <>
                  <FiLoader className="auth-spinner" aria-hidden="true" />
                  Logging in…
                </>
              ) : (
                <>
                  Log In
                  <FiArrowRight />
                </>
              )}
            </button>

          </form>


          <div className="auth-divider">
            <span>or</span>
          </div>


          <button
            type="button"
            className="guest-button"
            onClick={handleGuest}
            disabled={submitting}
          >
            Continue as Guest
          </button>

        </div>

      </section>

    </div>
  );
}


export default Login;
