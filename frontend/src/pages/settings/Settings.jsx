import { useState } from "react";
import { Link } from "react-router-dom";

import {
  FiUser,
  FiSliders,
  FiBell,
  FiShield,
  FiMonitor,
  FiSave,
  FiRotateCcw,
  FiCheckCircle,
  FiTrash2,
  FiInfo,
  FiLock
} from "react-icons/fi";

import { useAuth } from "../../context/AuthContext";
import PlanBadge from "../../components/common/PlanBadge";
import {
  DEFAULT_SETTINGS,
  DIFFICULTIES,
  DAILY_GOALS,
  getSettings,
  saveSettings,
  clearSettings
} from "../../services/settingsService";
import { clearPlanHistory } from "../plan/planData";
import { clearConversations } from "../coach/coachService";

const APPLICATIONS_KEY = "careerai_applications";

function clearApplications(userId) {
  try {
    const all = JSON.parse(localStorage.getItem(APPLICATIONS_KEY) || "{}") || {};
    delete all[userId];
    localStorage.setItem(APPLICATIONS_KEY, JSON.stringify(all));
  } catch {
    // ignore
  }
}

const sameSettings = (a, b) => Object.keys(DEFAULT_SETTINGS).every((key) => a[key] === b[key]);


function Settings() {
  const { user } = useAuth();

  const [saved, setSaved] = useState(() => getSettings(user?.id));
  const [draft, setDraft] = useState(saved);
  const [message, setMessage] = useState("");

  const dirty = !sameSettings(draft, saved);

  const set = (key, value) => {
    setDraft((current) => ({ ...current, [key]: value }));
    setMessage("");
  };

  const save = (event) => {
    event.preventDefault();
    const next = saveSettings(user?.id, draft);
    setSaved(next);
    setDraft(next);
    setMessage("Settings saved on this device.");
  };

  const discard = () => {
    setDraft(saved);
    setMessage("");
  };

  const resetDefaults = () => {
    setDraft({ ...DEFAULT_SETTINGS });
    setMessage("");
  };

  const clearLocalData = () => {
    const confirmed = window.confirm(
      "Clear your plan progress, applications, coach conversations and settings saved on this device? XP, streak and your profile are kept."
    );
    if (!confirmed) return;

    clearPlanHistory(user?.id);
    clearApplications(user?.id);
    clearConversations(user?.id);
    clearSettings(user?.id);

    const fresh = getSettings(user?.id);
    setSaved(fresh);
    setDraft(fresh);
    setMessage("Local CareerAI data for this account was cleared from this device.");
  };

  return (
    <div className="ua-page">

      <header className="ua-page-header">
        <div>
          <span className="ua-eyebrow">SETTINGS</span>
          <h1>Settings</h1>
          <p>Preferences are saved on this device and apply across your CareerAI workspace.</p>
        </div>
      </header>

      {message && (
        <div className="ua-alert success" role="status">
          <FiCheckCircle aria-hidden="true" />
          {message}
        </div>
      )}


      <form
        className="settings-form"
        onSubmit={save}
      >

        {/* ACCOUNT */}

        <section className="ua-card settings-section">
          <SettingsHeader icon={<FiUser />} title="Account" text="Your sign-in details and plan." />

          <dl className="settings-account">
            <div>
              <dt>Name</dt>
              <dd>{user?.name}</dd>
            </div>
            <div>
              <dt>Email</dt>
              <dd>{user?.email || "Guest account"}</dd>
            </div>
            <div>
              <dt>Plan</dt>
              <dd><PlanBadge plan={user?.plan} /></dd>
            </div>
          </dl>

          <div className="settings-links">
            <Link to="/profile" className="ua-button ghost small">Edit profile</Link>
            <Link to="/pricing" className="ua-button ghost small">View plans</Link>
          </div>

          <div className="settings-danger">
            <div>
              <strong>Delete account</strong>
              <small>Permanently removes your account and saved progress.</small>
            </div>
            <button
              type="button"
              className="ua-button small ghost"
              disabled
              aria-describedby="delete-backend-note"
            >
              <FiLock aria-hidden="true" />
              Delete Account
            </button>
            <span id="delete-backend-note" className="ua-demo-tag">Backend required</span>
          </div>
        </section>


        {/* PREPARATION */}

        <section className="ua-card settings-section">
          <SettingsHeader icon={<FiSliders />} title="Preparation Preferences" text="Defaults used by Practice and My Plan." />

          <fieldset className="settings-choice">
            <legend>Default difficulty</legend>
            <p className="settings-hint">Preselected when you open a practice module.</p>
            <div className="ua-segmented">
              {DIFFICULTIES.map((level) => (
                <label key={level} className={draft.defaultDifficulty === level ? "selected" : ""}>
                  <input
                    type="radio"
                    name="defaultDifficulty"
                    value={level}
                    checked={draft.defaultDifficulty === level}
                    onChange={() => set("defaultDifficulty", level)}
                  />
                  {level}
                </label>
              ))}
            </div>
          </fieldset>

          <fieldset className="settings-choice">
            <legend>Daily preparation goal</legend>
            <p className="settings-hint">Tasks to complete each day for the day to count toward your weekly goal.</p>
            <div className="ua-segmented">
              {DAILY_GOALS.map((goal) => (
                <label key={goal} className={draft.dailyGoal === goal ? "selected" : ""}>
                  <input
                    type="radio"
                    name="dailyGoal"
                    value={goal}
                    checked={draft.dailyGoal === goal}
                    onChange={() => set("dailyGoal", goal)}
                  />
                  {goal} tasks
                </label>
              ))}
            </div>
          </fieldset>
        </section>


        {/* NOTIFICATIONS */}

        <section className="ua-card settings-section" id="notifications">
          <SettingsHeader icon={<FiBell />} title="Notifications" text="Choose which reminders you want." />

          <Toggle
            id="emailReminders"
            label="Email reminders"
            description="A weekly summary of your plan and progress."
            checked={draft.emailReminders}
            onChange={(value) => set("emailReminders", value)}
          />
          <Toggle
            id="streakReminders"
            label="Streak reminders"
            description="A nudge on days you haven't practised yet."
            checked={draft.streakReminders}
            onChange={(value) => set("streakReminders", value)}
          />
          <Toggle
            id="interviewReminders"
            label="Interview reminders"
            description="Reminders for upcoming interviews in your Applications tracker."
            checked={draft.interviewReminders}
            onChange={(value) => set("interviewReminders", value)}
          />

          <p className="ua-footnote">
            <FiInfo aria-hidden="true" />
            Your choices are saved now. Reminders will be delivered once the notification service is connected.
          </p>
        </section>


        {/* PRIVACY */}

        <section className="ua-card settings-section">
          <SettingsHeader icon={<FiShield />} title="Privacy" text="Control how your data is used." />

          <Toggle
            id="useHistoryForRecommendations"
            label="Use my practice history for recommendations"
            description="Lets CareerAI personalise your plan from your results."
            checked={draft.useHistoryForRecommendations}
            onChange={(value) => set("useHistoryForRecommendations", value)}
          />

          <div className="settings-danger">
            <div>
              <strong>Clear data on this device</strong>
              <small>Removes plan progress, applications, coach conversations and settings for this account. XP, streak and profile are kept.</small>
            </div>
            <button
              type="button"
              className="ua-button small danger"
              onClick={clearLocalData}
            >
              <FiTrash2 aria-hidden="true" />
              Clear local data
            </button>
          </div>
        </section>


        {/* DISPLAY */}

        <section className="ua-card settings-section">
          <SettingsHeader icon={<FiMonitor />} title="Display Preferences" text="Adjust how the workspace looks." />

          <Toggle
            id="reduceMotion"
            label="Reduce motion"
            description="Turns off hover lifts and animated transitions in the workspace."
            checked={draft.reduceMotion}
            onChange={(value) => set("reduceMotion", value)}
          />
          <Toggle
            id="compactLayout"
            label="Compact layout"
            description="Tighter spacing to fit more on screen."
            checked={draft.compactLayout}
            onChange={(value) => set("compactLayout", value)}
          />
        </section>


        <div className={`settings-savebar${dirty ? " dirty" : ""}`}>
          <span>{dirty ? "You have unsaved changes." : "All changes saved."}</span>
          <div>
            <button type="button" className="ua-button ghost small" onClick={resetDefaults}>
              <FiRotateCcw aria-hidden="true" />
              Defaults
            </button>
            <button type="button" className="ua-button ghost small" onClick={discard} disabled={!dirty}>
              Discard
            </button>
            <button type="submit" className="ua-button primary small" disabled={!dirty}>
              <FiSave aria-hidden="true" />
              Save Settings
            </button>
          </div>
        </div>

      </form>

    </div>
  );
}


function SettingsHeader({ icon, title, text }) {
  return (
    <div className="settings-header">
      <span className="ua-card-title-icon-box" aria-hidden="true">{icon}</span>
      <div>
        <h2>{title}</h2>
        <p>{text}</p>
      </div>
    </div>
  );
}


function Toggle({ id, label, description, checked, onChange }) {
  return (
    <div className="settings-toggle">
      <div>
        <span id={`${id}-label`} className="settings-toggle-label">{label}</span>
        <small id={`${id}-description`}>{description}</small>
      </div>
      <button
        type="button"
        role="switch"
        className={`ua-switch${checked ? " on" : ""}`}
        aria-checked={checked}
        aria-labelledby={`${id}-label`}
        aria-describedby={`${id}-description`}
        onClick={() => onChange(!checked)}
      >
        <span aria-hidden="true"></span>
      </button>
    </div>
  );
}

export default Settings;
