import {
  useState
} from "react";

import {
  Link,
  useNavigate
} from "react-router-dom";

import {
  FiUser,
  FiMail,
  FiLock,
  FiEye,
  FiEyeOff,
  FiCheckCircle,
  FiArrowRight,
  FiLoader
} from "react-icons/fi";

import {
  useAuth
} from "../../context/AuthContext";

import {
  toApiError
} from "../../services/api";


const EMAIL_PATTERN =
  /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;


function validate(form) {

  const errors = {};

  if (!form.name.trim()) {
    errors.name =
      "Please enter your full name.";
  }

  if (!EMAIL_PATTERN.test(form.email.trim())) {
    errors.email =
      "Please enter a valid email address.";
  }

  if (form.password.length < 8) {
    errors.password =
      "Password must contain at least 8 characters.";
  } else if (form.password.length > 72) {
    errors.password =
      "Password must be at most 72 characters.";
  }

  if (!form.confirmPassword) {
    errors.confirmPassword =
      "Please confirm your password.";
  } else if (form.password !== form.confirmPassword) {
    errors.confirmPassword =
      "Passwords do not match.";
  }

  if (!form.terms) {
    errors.terms =
      "Please accept the terms to continue.";
  }

  return errors;
}


/** Message shown above the form for a failed registration request. */
function registerErrorMessage(apiError) {

  if (apiError.status === 0) {
    return "Unable to connect to CareerAI server.";
  }

  if (apiError.status === 409) {
    return "Email already registered. Please log in or use another email.";
  }

  // 400: the backend's own validation message (e.g. "Passwords do not match").
  return apiError.message;
}


function Register() {

  const navigate =
    useNavigate();


  const {
    register,
    continueAsGuest
  } = useAuth();


  const [form, setForm] =
    useState({

      name: "",

      email: "",

      password: "",

      confirmPassword: "",

      terms: false

    });


  const [showPassword,
    setShowPassword] =
    useState(false);


  const [error, setError] =
    useState("");


  // HTTP status of the last failed submit (409 shows a "Log in" link).
  const [errorStatus,
    setErrorStatus] =
    useState(null);


  const [fieldErrors,
    setFieldErrors] =
    useState({});


  const [submitting,
    setSubmitting] =
    useState(false);


  const handleChange =
    (event) => {

      const {
        name,
        value,
        checked,
        type
      } = event.target;


      setForm(
        (previous) => ({

          ...previous,

          [name]:
            type === "checkbox"
              ? checked
              : value

        })
      );


      // Editing the form clears the previous submit error.
      if (error) {
        setError("");
        setErrorStatus(null);
      }

      if (fieldErrors[name]) {
        setFieldErrors(
          (previous) => ({
            ...previous,
            [name]: undefined
          })
        );
      }

    };


  const handleSubmit =
    async (event) => {

      event.preventDefault();

      if (submitting) {
        return;
      }

      setError("");
      setErrorStatus(null);


      const errors =
        validate(form);

      setFieldErrors(errors);

      if (Object.keys(errors).length) {
        return;
      }


      setSubmitting(true);

      try {

        const result =
          await register({
            name: form.name.trim(),
            email: form.email.trim(),
            password: form.password,
            confirmPassword: form.confirmPassword
          });


        // The backend returns a JWT on registration, so the user is signed in.
        navigate(
          result?.requiresLogin
            ? "/login"
            : "/onboarding",
          { replace: true }
        );

      } catch (err) {

        const apiError =
          toApiError(err);

        const nextFieldErrors = {
          ...apiError.fieldErrors
        };

        if (apiError.status === 409) {
          nextFieldErrors.email =
            "This email is already registered.";
        }

        if (/passwords do not match/i.test(apiError.message)) {
          nextFieldErrors.confirmPassword =
            "Passwords do not match.";
        }

        setFieldErrors(nextFieldErrors);
        setError(
          registerErrorMessage(apiError)
        );
        setErrorStatus(apiError.status);
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


  return (
    <div className="auth-page">

      <section className="auth-info-panel">

        <span className="auth-info-tag">
          START YOUR JOURNEY
        </span>

        <h1>
          Prepare smarter for your
          <span>
            {" "}next opportunity.
          </span>
        </h1>

        <p>
          Create your free CareerAI account
          and turn your preparation into
          measurable progress.
        </p>


        <div className="auth-benefits">

          <div>
            <FiCheckCircle />
            Personalized career roadmap
          </div>

          <div>
            <FiCheckCircle />
            Save practice progress
          </div>

          <div>
            <FiCheckCircle />
            Earn XP and maintain streaks
          </div>

          <div>
            <FiCheckCircle />
            Save AI interview history
          </div>

          <div>
            <FiCheckCircle />
            Resume and job-match tools
          </div>

        </div>


        <div className="auth-plan-preview">

          <span>FREE PLAN</span>

          <strong>
            Start for ₹0
          </strong>

          <p>
            Upgrade later when you need
            additional AI interviews and
            higher usage limits.
          </p>

        </div>

      </section>


      <section className="auth-form-panel">

        <div className="auth-form-card">

          <div className="auth-form-heading">

            <span className="section-tag">
              CREATE ACCOUNT
            </span>

            <h2>
              Join CareerAI
            </h2>

            <p>
              Already registered?{" "}

              <Link to="/login">
                Log in
              </Link>
            </p>

          </div>


          {error && (

            <div
              className="auth-error"
              role="alert"
            >
              {error}

              {errorStatus === 409 && (
                <>
                  {" "}
                  <Link to="/login">
                    Log in
                  </Link>
                </>
              )}
            </div>

          )}


          <form
            className="auth-form"
            onSubmit={handleSubmit}
            noValidate
          >

            <AuthInput
              id="register-name"
              label="Full name"
              icon={<FiUser />}
              error={fieldErrors.name}
            >
              <input
                id="register-name"
                type="text"
                name="name"
                autoComplete="name"
                placeholder="Your full name"
                value={form.name}
                disabled={submitting}
                aria-invalid={Boolean(fieldErrors.name)}
                aria-describedby={fieldErrors.name ? "register-name-error" : undefined}
                onChange={handleChange}
              />
            </AuthInput>


            <AuthInput
              id="register-email"
              label="Email address"
              icon={<FiMail />}
              error={fieldErrors.email}
            >
              <input
                id="register-email"
                type="email"
                name="email"
                autoComplete="email"
                placeholder="you@example.com"
                value={form.email}
                disabled={submitting}
                aria-invalid={Boolean(fieldErrors.email)}
                aria-describedby={fieldErrors.email ? "register-email-error" : undefined}
                onChange={handleChange}
              />
            </AuthInput>


            <AuthInput
              id="register-password"
              label="Password"
              icon={<FiLock />}
              error={fieldErrors.password}
            >

              <input
                id="register-password"
                type={
                  showPassword
                    ? "text"
                    : "password"
                }
                name="password"
                autoComplete="new-password"
                placeholder="Minimum 8 characters"
                value={form.password}
                disabled={submitting}
                aria-invalid={Boolean(fieldErrors.password)}
                aria-describedby={fieldErrors.password ? "register-password-error" : undefined}
                onChange={handleChange}
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

            </AuthInput>


            <AuthInput
              id="register-confirm"
              label="Confirm password"
              icon={<FiLock />}
              error={fieldErrors.confirmPassword}
            >

              <input
                id="register-confirm"
                type="password"
                name="confirmPassword"
                autoComplete="new-password"
                placeholder="Repeat password"
                value={
                  form.confirmPassword
                }
                disabled={submitting}
                aria-invalid={Boolean(fieldErrors.confirmPassword)}
                aria-describedby={fieldErrors.confirmPassword ? "register-confirm-error" : undefined}
                onChange={handleChange}
              />

            </AuthInput>


            <label className="auth-checkbox">

              <input
                type="checkbox"
                name="terms"
                checked={form.terms}
                disabled={submitting}
                aria-invalid={Boolean(fieldErrors.terms)}
                aria-describedby={fieldErrors.terms ? "register-terms-error" : undefined}
                onChange={handleChange}
              />

              <span>
                I agree to the Terms of
                Service and Privacy Policy.
              </span>

            </label>

            {fieldErrors.terms && (
              <span
                id="register-terms-error"
                className="auth-field-error"
              >
                {fieldErrors.terms}
              </span>
            )}


            <button
              type="submit"
              className="auth-submit"
              disabled={submitting}
              aria-busy={submitting}
            >
              {submitting ? (
                <>
                  <FiLoader className="auth-spinner" aria-hidden="true" />
                  Creating account…
                </>
              ) : (
                <>
                  Create Free Account
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


          <p className="guest-note">
            Guest mode allows limited CareerAI
            access. Create an account later to
            save your progress.
          </p>

        </div>

      </section>

    </div>
  );
}


function AuthInput({
  id,
  label,
  icon,
  error,
  children
}) {

  return (
    <div className="auth-field">

      <label htmlFor={id}>
        {label}
      </label>

      <div className={`auth-input${error ? " has-error" : ""}`}>

        {icon}

        {children}

      </div>

      {error && (
        <span
          id={`${id}-error`}
          className="auth-field-error"
        >
          {error}
        </span>
      )}

    </div>
  );
}


export default Register;
