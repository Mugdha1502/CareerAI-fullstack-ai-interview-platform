import { useState } from "react";
import { Link } from "react-router-dom";

import {
  FiArrowRight,
  FiSend,
  FiCheckCircle,
  FiAlertCircle,
  FiHelpCircle,
  FiTool,
  FiZap,
  FiInfo,
  FiRefreshCw
} from "react-icons/fi";

const subjects = [
  "General Enquiry",
  "Technical Issue",
  "Feature Suggestion",
  "Account Help",
  "Resume Tools",
  "AI Interview",
  "Practice Module",
  "Other"
];

const infoCards = [
  {
    icon: <FiHelpCircle />,
    title: "Platform Support",
    text: "Questions about CareerAI features and usage."
  },
  {
    icon: <FiTool />,
    title: "Technical Feedback",
    text: "Report UI, routing or functionality issues."
  },
  {
    icon: <FiZap />,
    title: "Suggestions",
    text: "Share ideas for improving CareerAI."
  }
];

const emptyForm = {
  name: "",
  email: "",
  subject: "",
  message: "",
  acknowledged: false
};

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

function validate(values) {
  const errors = {};

  if (!values.name.trim()) {
    errors.name = "Please enter your full name.";
  }

  if (!values.email.trim()) {
    errors.email = "Please enter your email address.";
  } else if (!EMAIL_PATTERN.test(values.email.trim())) {
    errors.email = "Please enter a valid email address.";
  }

  if (!values.subject) {
    errors.subject = "Please choose a subject.";
  }

  if (values.message.trim().length < 10) {
    errors.message = "Message must be at least 10 characters.";
  }

  if (!values.acknowledged) {
    errors.acknowledged = "Please confirm you understand this form runs in demo mode.";
  }

  return errors;
}


function Contact() {
  const [values, setValues] = useState(emptyForm);
  const [errors, setErrors] = useState({});
  const [submitted, setSubmitted] = useState(false);

  const updateField = (event) => {
    const { name, value, type, checked } = event.target;
    const nextValues = { ...values, [name]: type === "checkbox" ? checked : value };

    setValues(nextValues);

    // Once a field has an error, re-check it as the user corrects it.
    if (errors[name]) {
      setErrors((current) => {
        const next = { ...current };
        const fieldError = validate(nextValues)[name];

        if (fieldError) next[name] = fieldError;
        else delete next[name];

        return next;
      });
    }
  };

  const handleSubmit = (event) => {
    event.preventDefault();

    const nextErrors = validate(values);
    setErrors(nextErrors);

    if (Object.keys(nextErrors).length > 0) {
      const firstField = Object.keys(nextErrors)[0];
      document.getElementById(`contact-${firstField}`)?.focus();
      return;
    }

    // TODO backend: POST /api/contact — nothing is sent in demo mode.
    setSubmitted(true);
  };

  const resetForm = () => {
    setValues(emptyForm);
    setErrors({});
    setSubmitted(false);
  };

  const fieldProps = (name) => ({
    id: `contact-${name}`,
    name,
    "aria-invalid": Boolean(errors[name]),
    "aria-describedby": errors[name] ? `contact-${name}-error` : undefined
  });

  const fieldError = (name) =>
    errors[name] && (
      <span
        id={`contact-${name}-error`}
        className="contact-error"
      >
        <FiAlertCircle aria-hidden="true" />
        {errors[name]}
      </span>
    );

  return (
    <div className="contact-page">

      {/* HERO */}

      <section className="contact-hero">

        <div className="section-container contact-hero-inner">

          <span className="section-tag">
            CONTACT CAREERAI
          </span>

          <h1>Have a question, suggestion or feedback?</h1>

          <p>
            Send a message about the platform, technical issues, feature
            ideas or general enquiries.
          </p>

        </div>

      </section>


      <section className="home-section contact-main-section">

        <div className="section-container contact-layout">

          {/* FORM / SUCCESS */}

          <div className="contact-form-card">

            {submitted ? (
              <div
                className="contact-success"
                role="status"
              >

                <span
                  className="contact-success-icon"
                  aria-hidden="true"
                >
                  <FiCheckCircle />
                </span>

                <h2>Message prepared successfully</h2>

                <p>
                  The contact form is currently running in demo mode. Backend
                  message delivery will be connected later.
                </p>

                <dl className="contact-summary">
                  <div>
                    <dt>Name</dt>
                    <dd>{values.name.trim()}</dd>
                  </div>
                  <div>
                    <dt>Email</dt>
                    <dd>{values.email.trim()}</dd>
                  </div>
                  <div>
                    <dt>Subject</dt>
                    <dd>{values.subject}</dd>
                  </div>
                </dl>

                <button
                  type="button"
                  className="primary-button"
                  onClick={resetForm}
                >
                  <FiRefreshCw aria-hidden="true" />
                  Send Another Message
                </button>

              </div>
            ) : (
              <form
                className="contact-form"
                onSubmit={handleSubmit}
                noValidate
              >

                <div className="contact-form-head">
                  <h2>Send a message</h2>
                  <p>All fields are required.</p>
                </div>

                <div className="contact-row">

                  <div className={`contact-field${errors.name ? " has-error" : ""}`}>
                    <label htmlFor="contact-name">Full Name</label>
                    <input
                      {...fieldProps("name")}
                      type="text"
                      autoComplete="name"
                      placeholder="Your full name"
                      value={values.name}
                      onChange={updateField}
                    />
                    {fieldError("name")}
                  </div>

                  <div className={`contact-field${errors.email ? " has-error" : ""}`}>
                    <label htmlFor="contact-email">Email Address</label>
                    <input
                      {...fieldProps("email")}
                      type="email"
                      autoComplete="email"
                      placeholder="you@example.com"
                      value={values.email}
                      onChange={updateField}
                    />
                    {fieldError("email")}
                  </div>

                </div>

                <div className={`contact-field${errors.subject ? " has-error" : ""}`}>
                  <label htmlFor="contact-subject">Subject</label>
                  <select
                    {...fieldProps("subject")}
                    value={values.subject}
                    onChange={updateField}
                  >
                    <option value="">Choose a subject</option>
                    {subjects.map((subject) => (
                      <option key={subject} value={subject}>{subject}</option>
                    ))}
                  </select>
                  {fieldError("subject")}
                </div>

                <div className={`contact-field${errors.message ? " has-error" : ""}`}>
                  <label htmlFor="contact-message">Message</label>
                  <textarea
                    {...fieldProps("message")}
                    rows={6}
                    placeholder="How can we help?"
                    value={values.message}
                    onChange={updateField}
                  />
                  <span className="contact-counter">
                    {values.message.trim().length} characters (minimum 10)
                  </span>
                  {fieldError("message")}
                </div>

                <div className={`contact-check${errors.acknowledged ? " has-error" : ""}`}>
                  <input
                    {...fieldProps("acknowledged")}
                    type="checkbox"
                    checked={values.acknowledged}
                    onChange={updateField}
                  />
                  <label htmlFor="contact-acknowledged">
                    I understand this is currently a frontend demo and messages
                    are not yet sent to a backend.
                  </label>
                </div>
                {fieldError("acknowledged")}

                <button
                  type="submit"
                  className="primary-button contact-submit"
                >
                  <FiSend aria-hidden="true" />
                  Send Message
                </button>

              </form>
            )}

          </div>


          {/* INFO */}

          <aside className="contact-aside">

            <div className="contact-demo-note">
              <FiInfo aria-hidden="true" />
              <p>
                Demo mode: messages are validated in your browser but are not
                delivered anywhere yet.
              </p>
            </div>

            {infoCards.map((card) => (
              <div
                key={card.title}
                className="contact-info-card"
              >
                <span
                  className="contact-info-icon"
                  aria-hidden="true"
                >
                  {card.icon}
                </span>
                <div>
                  <strong>{card.title}</strong>
                  <p>{card.text}</p>
                </div>
              </div>
            ))}

          </aside>

        </div>

      </section>


      {/* QUICK HELP */}

      <section className="info-page-cta-section">

        <div className="section-container">

          <div className="info-page-cta">

            <div>
              <span className="section-tag light">
                QUICK HELP
              </span>
              <h2>Looking for an answer first?</h2>
            </div>

            <div className="info-page-cta-actions">

              <Link
                to="/faq"
                className="cta-white-button"
              >
                View FAQ
                <FiArrowRight aria-hidden="true" />
              </Link>

              <Link
                to="/resources"
                className="info-page-cta-outline"
              >
                Explore Resources
              </Link>

            </div>

          </div>

        </div>

      </section>

    </div>
  );
}

export default Contact;
