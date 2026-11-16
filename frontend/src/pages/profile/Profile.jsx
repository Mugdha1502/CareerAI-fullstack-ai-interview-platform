import { useEffect, useState } from "react";
import { Link } from "react-router-dom";

import {
  FiUser,
  FiBookOpen,
  FiBriefcase,
  FiCode,
  FiTarget,
  FiFlag,
  FiSliders,
  FiEdit2,
  FiSave,
  FiX,
  FiCheckCircle,
  FiCircle,
  FiInfo,
  FiAlertCircle,
  FiLoader,
  FiRefreshCw
} from "react-icons/fi";

import { useAuth } from "../../context/AuthContext";
import PlanBadge from "../../components/common/PlanBadge";
import { toApiError } from "../../services/api";
import { getProfile, splitSkills } from "../../services/profileService";
import {
  careerTracks,
  getTrackByTitle,
  saveSelectedTrack
} from "../career/careerTrackData";

// Option lists match onboarding and the backend's allowed values.
const STATUS_OPTIONS = ["Student", "Fresher", "Working Professional", "Career Switcher"];
const EXPERIENCE_OPTIONS = ["No professional experience", "Less than 1 year", "1-3 years", "3-5 years", "5+ years"];
const GOAL_OPTIONS = ["Interview Preparation", "Placement Preparation", "Improve Technical Skills", "Career Switch", "Job Search"];
const DAILY_TIME_OPTIONS = [
  { value: "30", label: "30 minutes" },
  { value: "60", label: "1 hour" },
  { value: "90", label: "1.5 hours" },
  { value: "120", label: "2 hours" }
];
const DIFFICULTY_OPTIONS = ["Beginner", "Intermediate", "Advanced"];
const TRACK_TITLES = Object.values(careerTracks).map((track) => track.title);

const COMPLETION_FIELDS = [
  { key: "status", label: "Current role" },
  { key: "education", label: "Education" },
  { key: "experience", label: "Experience level" },
  { key: "skills", label: "Skills" },
  { key: "targetRole", label: "Target role" },
  { key: "goal", label: "Career goal" },
  { key: "dailyTime", label: "Daily preparation time" },
  { key: "interviewDifficulty", label: "Preferred difficulty" }
];

// Backend field names → form keys, for showing validation errors inline.
const API_FIELD_TO_FORM = {
  currentRole: "status",
  education: "education",
  experienceLevel: "experience",
  skills: "skills",
  targetRole: "targetRole",
  careerGoal: "goal",
  dailyPreparationMinutes: "dailyTime",
  preferredDifficulty: "interviewDifficulty"
};

function toForm(profile) {
  const p = profile || {};

  return {
    status: p.status || "",
    education: p.education || "",
    experience: p.experience || "",
    skills: p.skills || "",
    // Guests start with a placeholder role that isn't a real track.
    targetRole: TRACK_TITLES.includes(p.targetRole) ? p.targetRole : "",
    goal: p.goal || "",
    dailyTime: p.dailyTime || "",
    interviewDifficulty: p.interviewDifficulty || ""
  };
}

function mapFieldErrors(fieldErrors) {
  const mapped = {};

  Object.entries(fieldErrors || {}).forEach(([field, message]) => {
    // "skills[3]" → "skills"
    const key = API_FIELD_TO_FORM[field.replace(/\[\d+\]$/, "")];
    if (key && !mapped[key]) mapped[key] = message;
  });

  return mapped;
}


function Profile() {
  const { user, isGuest, saveProfile } = useAuth();

  // Registered users load from GET /api/profile/me; guests use their local session.
  const [loading, setLoading] = useState(!isGuest);
  const [loadError, setLoadError] = useState("");
  const [reloadKey, setReloadKey] = useState(0);
  const [savedProfile, setSavedProfile] = useState(() => (isGuest ? user?.profile || null : null));

  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState(() => toForm(isGuest ? user?.profile : null));
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState("");
  const [fieldErrors, setFieldErrors] = useState({});
  const [savedMessage, setSavedMessage] = useState("");

  useEffect(() => {
    if (isGuest) return undefined;

    let cancelled = false;

    getProfile()
      .then(({ profile }) => {
        if (cancelled) return;
        setSavedProfile(profile);
        setForm(toForm(profile));
        setLoadError("");
      })
      .catch((error) => {
        if (cancelled) return;
        const { status, message } = toApiError(error);
        setLoadError(status === 0 ? "Unable to connect to CareerAI server." : message);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [isGuest, reloadKey]);

  const saved = toForm(savedProfile);
  const values = editing ? form : saved;

  const completed = COMPLETION_FIELDS.filter((field) => String(saved[field.key]).trim());
  const completion = Math.round((completed.length / COMPLETION_FIELDS.length) * 100);

  // Show a saved value (e.g. 45 minutes) even if it isn't one of the standard options.
  const dailyOptions = DAILY_TIME_OPTIONS.some((option) => option.value === values.dailyTime) || !values.dailyTime
    ? DAILY_TIME_OPTIONS
    : [...DAILY_TIME_OPTIONS, { value: values.dailyTime, label: `${values.dailyTime} minutes` }];

  const update = (key) => (event) => {
    setForm((current) => ({ ...current, [key]: event.target.value }));
    setSaveError("");
    if (fieldErrors[key]) setFieldErrors((current) => ({ ...current, [key]: undefined }));
  };

  const startEditing = () => {
    setForm(toForm(savedProfile));
    setSavedMessage("");
    setSaveError("");
    setFieldErrors({});
    setEditing(true);
  };

  const cancel = () => {
    setForm(toForm(savedProfile));
    setSaveError("");
    setFieldErrors({});
    setEditing(false);
  };

  const validate = () => {
    const errors = {};
    const skills = splitSkills(form.skills);

    if (form.education.trim().length > 150) errors.education = "Education must be at most 150 characters.";
    if (skills.length > 30) errors.skills = "Add at most 30 skills.";
    else if (skills.some((skill) => skill.length > 60)) errors.skills = "Each skill must be at most 60 characters.";

    return errors;
  };

  const save = async (event) => {
    event.preventDefault();
    if (saving) return;

    const errors = validate();
    setFieldErrors(errors);

    if (Object.keys(errors).length) {
      document.getElementById(`profile-${Object.keys(errors)[0]}`)?.focus();
      return;
    }

    const nextProfile = {
      ...form,
      education: form.education.trim(),
      skills: splitSkills(form.skills).join(", ")
    };

    setSaving(true);
    setSaveError("");

    try {
      // Registered users: PUT /api/profile/me. Guests: local guest session.
      const updated = await saveProfile(nextProfile);

      setSavedProfile(updated.profile);
      setForm(toForm(updated.profile));

      // Keep Career Tracks' selected track in sync with the saved target role.
      const track = getTrackByTitle(updated.profile?.targetRole);
      if (track) saveSelectedTrack(track);

      setEditing(false);
      setSavedMessage(isGuest ? "Profile saved on this device." : "Profile saved.");
    } catch (error) {
      const apiError = toApiError(error);
      setFieldErrors(mapFieldErrors(apiError.fieldErrors));
      setSaveError(apiError.status === 0 ? "Unable to connect to CareerAI server." : apiError.message);
    } finally {
      setSaving(false);
    }
  };

  const retryLoad = () => {
    setLoading(true);
    setLoadError("");
    setReloadKey((key) => key + 1);
  };

  const readOnly = !editing || saving;

  const fieldError = (key) =>
    fieldErrors[key] && (
      <span id={`profile-${key}-error`} className="ua-field-error">
        <FiAlertCircle aria-hidden="true" />
        {fieldErrors[key]}
      </span>
    );

  const errorProps = (key) => ({
    "aria-invalid": Boolean(fieldErrors[key]),
    "aria-describedby": fieldErrors[key] ? `profile-${key}-error` : undefined
  });

  return (
    <div className="ua-page">

      <header className="ua-page-header">
        <div>
          <span className="ua-eyebrow">PROFILE</span>
          <h1>Your profile</h1>
          <p>Your background and goals shape your plan, practice and interview recommendations.</p>
        </div>

        {!editing && !loading && !loadError && (
          <div className="ua-header-actions">
            <button
              type="button"
              className="ua-button primary"
              onClick={startEditing}
            >
              <FiEdit2 aria-hidden="true" />
              Edit Profile
            </button>
          </div>
        )}
      </header>


      {savedMessage && !editing && (
        <div className="ua-alert success" role="status">
          <FiCheckCircle aria-hidden="true" />
          {savedMessage}
        </div>
      )}

      {isGuest && (
        <div className="ua-demo-banner">
          <FiInfo aria-hidden="true" />
          <p>
            You're using a guest profile. Changes are saved on this device only.{" "}
            <Link to="/register">Create a free account</Link> to keep them with your account.
          </p>
        </div>
      )}


      {loading ? (
        <section className="ua-card">
          <div className="ua-empty" role="status">
            <FiLoader className="auth-spinner" aria-hidden="true" />
            <p>Loading your profile…</p>
          </div>
        </section>
      ) : loadError ? (
        <section className="ua-card">
          <div className="ua-empty" role="alert">
            <FiAlertCircle aria-hidden="true" />
            <p>{loadError}</p>
            <button type="button" className="ua-button primary" onClick={retryLoad}>
              <FiRefreshCw aria-hidden="true" />
              Try again
            </button>
          </div>
        </section>
      ) : (

        <div className="profile-layout">

          <form
            className="profile-form"
            onSubmit={save}
            noValidate
          >

            {/* ACCOUNT (from AuthContext, read-only) */}

            <section className="ua-card">
              <SectionHeader icon={<FiUser />} kicker="PERSONAL INFORMATION" title="About you" />

              <div className="ua-form-grid">

                <div className="ua-field">
                  <label htmlFor="profile-account-name">Full Name</label>
                  <input
                    id="profile-account-name"
                    type="text"
                    value={user?.name || ""}
                    readOnly
                  />
                </div>

                <div className="ua-field">
                  <label htmlFor="profile-email">Email</label>
                  <input
                    id="profile-email"
                    type="email"
                    value={user?.email || "Guest account — no email"}
                    readOnly
                    aria-describedby="profile-email-note"
                  />
                  <small id="profile-email-note">Name and email changes will be added with account settings.</small>
                </div>

                <div className={`ua-field${fieldErrors.status ? " has-error" : ""}`}>
                  <label htmlFor="profile-status">Current Role / Student</label>
                  <select
                    id="profile-status"
                    value={values.status}
                    onChange={update("status")}
                    disabled={readOnly}
                    {...errorProps("status")}
                  >
                    <option value="">Not set</option>
                    {STATUS_OPTIONS.map((option) => <option key={option}>{option}</option>)}
                  </select>
                  {fieldError("status")}
                </div>

                <div className="ua-field">
                  <span className="ua-field-label">Plan</span>
                  <div className="profile-plan">
                    <PlanBadge plan={user?.plan} />
                    <Link to="/pricing" className="ua-card-link">View plans</Link>
                  </div>
                </div>

              </div>
            </section>


            {/* EDUCATION + EXPERIENCE */}

            <section className="ua-card">
              <SectionHeader icon={<FiBookOpen />} kicker="EDUCATION & EXPERIENCE" title="Background" />

              <div className="ua-form-grid">

                <div className={`ua-field${fieldErrors.education ? " has-error" : ""}`}>
                  <label htmlFor="profile-education">Education</label>
                  <input
                    id="profile-education"
                    type="text"
                    placeholder="e.g. B.E. Computer Engineering"
                    value={values.education}
                    onChange={update("education")}
                    readOnly={readOnly}
                    {...errorProps("education")}
                  />
                  {fieldError("education")}
                </div>

                <div className={`ua-field${fieldErrors.experience ? " has-error" : ""}`}>
                  <label htmlFor="profile-experience">
                    <FiBriefcase aria-hidden="true" /> Experience Level
                  </label>
                  <select
                    id="profile-experience"
                    value={values.experience}
                    onChange={update("experience")}
                    disabled={readOnly}
                    {...errorProps("experience")}
                  >
                    <option value="">Not set</option>
                    {EXPERIENCE_OPTIONS.map((option) => <option key={option}>{option}</option>)}
                  </select>
                  {fieldError("experience")}
                </div>

              </div>
            </section>


            {/* SKILLS */}

            <section className="ua-card">
              <SectionHeader icon={<FiCode />} kicker="SKILLS" title="What you already know" />

              <div className={`ua-field${fieldErrors.skills ? " has-error" : ""}`}>
                <label htmlFor="profile-skills">Skills</label>
                <input
                  id="profile-skills"
                  type="text"
                  placeholder="Java, SQL, React"
                  value={values.skills}
                  onChange={update("skills")}
                  readOnly={readOnly}
                  aria-invalid={Boolean(fieldErrors.skills)}
                  aria-describedby={fieldErrors.skills ? "profile-skills-error" : "profile-skills-hint"}
                />
                <small id="profile-skills-hint">Separate skills with commas.</small>
                {fieldError("skills")}
              </div>

              {splitSkills(values.skills).length > 0 ? (
                <ul className="profile-skill-chips" aria-label="Skills">
                  {splitSkills(values.skills).map((skill) => <li key={skill}>{skill}</li>)}
                </ul>
              ) : (
                !editing && <p className="ua-muted">No skills added yet.</p>
              )}
            </section>


            {/* TARGET + GOAL */}

            <section className="ua-card">
              <SectionHeader icon={<FiTarget />} kicker="TARGET ROLE & CAREER GOAL" title="Where you're heading" />

              <div className="ua-form-grid">

                <div className={`ua-field${fieldErrors.targetRole ? " has-error" : ""}`}>
                  <label htmlFor="profile-targetRole">Target Role</label>
                  <select
                    id="profile-targetRole"
                    value={values.targetRole}
                    onChange={update("targetRole")}
                    disabled={readOnly}
                    {...errorProps("targetRole")}
                  >
                    <option value="">Not set</option>
                    {TRACK_TITLES.map((title) => <option key={title}>{title}</option>)}
                  </select>
                  {fieldError("targetRole")}
                  {!editing && getTrackByTitle(saved.targetRole) && (
                    <Link
                      to={`/career-tracks/${getTrackByTitle(saved.targetRole).id}`}
                      className="ua-card-link"
                    >
                      View roadmap
                    </Link>
                  )}
                </div>

                <div className={`ua-field${fieldErrors.goal ? " has-error" : ""}`}>
                  <label htmlFor="profile-goal">
                    <FiFlag aria-hidden="true" /> Career Goal
                  </label>
                  <select
                    id="profile-goal"
                    value={values.goal}
                    onChange={update("goal")}
                    disabled={readOnly}
                    {...errorProps("goal")}
                  >
                    <option value="">Not set</option>
                    {GOAL_OPTIONS.map((option) => <option key={option}>{option}</option>)}
                  </select>
                  {fieldError("goal")}
                </div>

              </div>
            </section>


            {/* PREFERENCES */}

            <section className="ua-card">
              <SectionHeader icon={<FiSliders />} kicker="PREPARATION PREFERENCES" title="How you like to prepare" />

              <div className="ua-form-grid">

                <div className={`ua-field${fieldErrors.dailyTime ? " has-error" : ""}`}>
                  <label htmlFor="profile-dailyTime">Daily Preparation Time</label>
                  <select
                    id="profile-dailyTime"
                    value={values.dailyTime}
                    onChange={update("dailyTime")}
                    disabled={readOnly}
                    {...errorProps("dailyTime")}
                  >
                    <option value="">Not set</option>
                    {dailyOptions.map((option) => (
                      <option key={option.value} value={option.value}>{option.label}</option>
                    ))}
                  </select>
                  {fieldError("dailyTime")}
                </div>

                <div className={`ua-field${fieldErrors.interviewDifficulty ? " has-error" : ""}`}>
                  <label htmlFor="profile-interviewDifficulty">Preferred Difficulty</label>
                  <select
                    id="profile-interviewDifficulty"
                    value={values.interviewDifficulty}
                    onChange={update("interviewDifficulty")}
                    disabled={readOnly}
                    {...errorProps("interviewDifficulty")}
                  >
                    <option value="">Not set</option>
                    {DIFFICULTY_OPTIONS.map((option) => <option key={option}>{option}</option>)}
                  </select>
                  {fieldError("interviewDifficulty")}
                </div>

              </div>
            </section>


            {editing && (
              <div className="profile-actions">
                {saveError && (
                  <span className="ua-field-error profile-save-error" role="alert">
                    <FiAlertCircle aria-hidden="true" />
                    {saveError}
                  </span>
                )}
                <button
                  type="button"
                  className="ua-button ghost"
                  onClick={cancel}
                  disabled={saving}
                >
                  <FiX aria-hidden="true" />
                  Cancel
                </button>
                <button
                  type="submit"
                  className="ua-button primary"
                  disabled={saving}
                  aria-busy={saving}
                >
                  {saving
                    ? <FiLoader className="auth-spinner" aria-hidden="true" />
                    : <FiSave aria-hidden="true" />}
                  {saving ? "Saving…" : "Save Profile"}
                </button>
              </div>
            )}

          </form>


          {/* COMPLETION */}

          <aside className="profile-aside">

            <section className="ua-card profile-completion">

              <span className="ua-card-kicker">PROFILE COMPLETION</span>

              <div
                className="profile-completion-ring"
                style={{ "--score": `${completion * 3.6}deg` }}
              >
                <strong>{completion}%</strong>
              </div>

              <p>{completed.length} of {COMPLETION_FIELDS.length} details added</p>

              <ul className="profile-checklist">
                {COMPLETION_FIELDS.map((field) => {
                  const done = Boolean(String(saved[field.key]).trim());

                  return (
                    <li key={field.key} className={done ? "done" : ""}>
                      {done ? <FiCheckCircle aria-hidden="true" /> : <FiCircle aria-hidden="true" />}
                      {field.label}
                      <span className="sr-only">{done ? " (added)" : " (missing)"}</span>
                    </li>
                  );
                })}
              </ul>

              {completion < 100 && !editing && (
                <button
                  type="button"
                  className="ua-button ghost full"
                  onClick={startEditing}
                >
                  Complete profile
                </button>
              )}

            </section>

          </aside>

        </div>
      )}

    </div>
  );
}


function SectionHeader({ icon, kicker, title }) {
  return (
    <div className="ua-card-header">
      <div className="ua-card-title-icon">
        <span aria-hidden="true">{icon}</span>
        <div>
          <span className="ua-card-kicker">{kicker}</span>
          <h2>{title}</h2>
        </div>
      </div>
    </div>
  );
}

export default Profile;
