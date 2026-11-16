import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";

import {
  FiTarget,
  FiCheckCircle,
  FiAlertCircle,
  FiTrendingUp,
  FiArrowRight,
  FiUser,
  FiPlay,
  FiClipboard,
  FiMic,
  FiInfo,
  FiBookOpen,
  FiRefreshCw,
  FiHelpCircle,
  FiLoader
} from "react-icons/fi";

import { getToken, toApiError } from "../../services/api";
import { getSkillGap } from "../../services/skillGapService";

/* =====================================================
   SKILL GAP — GET /api/skill-gap
   Scores come only from saved practice. Skills without practice data are
   shown as "Not assessed yet" (or self-reported) and never given a number.
===================================================== */

const statusClass = {
  STRONG: "strong",
  GOOD: "good",
  IMPROVE: "improve",
  PRIORITY: "priority",
  SELF_REPORTED: "unassessed",
  UNASSESSED: "unassessed"
};

const statusLabel = {
  STRONG: "Strong",
  GOOD: "Good",
  IMPROVE: "Improve",
  PRIORITY: "Priority",
  SELF_REPORTED: "Self-reported",
  UNASSESSED: "Not assessed"
};

const isAssessed = (skill) => skill.score != null;


function SkillGap() {
  const [searchParams] = useSearchParams();
  const trackId = searchParams.get("track") || undefined;
  const signedIn = Boolean(getToken());

  const [state, setState] = useState({ loading: signedIn, error: "", gap: null });
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    if (!signedIn) return undefined;

    let cancelled = false;

    getSkillGap(trackId)
      .then((gap) => {
        if (!cancelled) setState({ loading: false, error: "", gap });
      })
      .catch((error) => {
        if (cancelled) return;
        const { status, message } = toApiError(error);
        setState({ loading: false, error: status === 0 ? "Unable to connect to CareerAI server." : message, gap: null });
      });

    return () => {
      cancelled = true;
    };
  }, [signedIn, trackId, reloadKey]);

  const retry = () => {
    setState({ loading: true, error: "", gap: null });
    setReloadKey((key) => key + 1);
  };

  return (
    <div className="tool-page skill-gap-page">

      <div className="section-container">

        <div className="tool-page-header">

          <span className="section-tag">
            SKILL GAP ANALYSIS
          </span>

          <h1>
            Understand what's between you and your target role
          </h1>

          <p>
            CareerAI compares the skills your target role requires with your
            saved practice results and profile.
          </p>

        </div>

        {!signedIn ? (
          <SkillGapStatus
            icon={<FiInfo />}
            message="Sign in to see your skill gap. It is calculated from the practice you complete while signed in."
            action={<Link to="/login" className="primary-button">Log in</Link>}
          />
        ) : state.loading ? (
          <SkillGapStatus icon={<FiLoader className="auth-spinner" />} message="Analysing your skills…" role="status" />
        ) : state.error ? (
          <SkillGapStatus
            icon={<FiAlertCircle />}
            message={state.error}
            role="alert"
            action={(
              <button type="button" className="primary-button" onClick={retry}>
                <FiRefreshCw aria-hidden="true" />
                Try again
              </button>
            )}
          />
        ) : (
          <SkillGapContent gap={state.gap} previewing={Boolean(trackId)} />
        )}

        <ReadinessMethod />

      </div>

    </div>
  );
}


function SkillGapStatus({ icon, message, action, role }) {
  return (
    <div className="skill-gap-state" role={role}>
      <span aria-hidden="true">{icon}</span>
      <p>{message}</p>
      {action}
    </div>
  );
}


function SkillGapContent({ gap, previewing }) {
  const skills = gap.skills;
  const strong = skills.filter((skill) => skill.status === "STRONG");
  const improving = skills.filter((skill) => skill.status === "GOOD" || skill.status === "IMPROVE");
  const priority = skills.filter((skill) => skill.status === "PRIORITY");
  const notAssessed = skills.filter((skill) => !isAssessed(skill));

  const weakest = skills
    .filter((skill) => skill.status === "PRIORITY" || skill.status === "IMPROVE")
    .sort((a, b) => a.score - b.score);
  const focusList = (weakest.length ? weakest : notAssessed.filter((skill) => skill.practicable)).slice(0, 3);

  const next = gap.recommendedNextSkill
    ? skills.find((skill) => skill.name === gap.recommendedNextSkill)
    : null;

  const groups = [
    { key: "strong", title: "Strong Skills", icon: <FiCheckCircle />, items: strong, empty: "No strong skills yet — keep practising." },
    { key: "improve", title: "Improving Skills", icon: <FiTrendingUp />, items: improving, empty: "Nothing in progress yet." },
    { key: "priority", title: "Priority Skills", icon: <FiAlertCircle />, items: priority, empty: "No priority gaps among assessed skills." },
    { key: "unassessed", title: "Not Assessed Yet", icon: <FiHelpCircle />, items: notAssessed, empty: "Every required skill has a score." }
  ];

  const summaryText = () => {
    if (gap.assessedSkillCount === 0) {
      return "None of the required skills has a practice score yet. Start with the recommended skill below to get your first readiness score.";
    }

    const parts = [];
    if (gap.strongSkills.length) parts.push(`You are strong in ${gap.strongSkills.join(", ")}.`);
    if (gap.prioritySkills.length) parts.push(`Focus next on ${gap.prioritySkills.join(", ")}.`);
    if (gap.unassessedSkills.length) parts.push(`${gap.unassessedSkills.length} required skills are not assessed yet.`);
    return parts.join(" ");
  };

  return (
    <>

      {!gap.targetRoleSelected && !previewing && (
        <div className="skill-gap-demo-banner">
          <FiInfo aria-hidden="true" />
          <p>
            <strong>No target role set.</strong> Showing {gap.targetRole} as an example.{" "}
            <Link to="/profile">Choose your target role</Link> to see your own gap.
          </p>
        </div>
      )}


      <div className="skill-gap-summary">

        <div
          className="skill-gap-score"
          style={{ "--readiness": `${(gap.readiness ?? 0) * 3.6}deg` }}
        >

          <FiTarget />

          <strong>
            {gap.readiness != null ? `${gap.readiness}%` : "—"}
          </strong>

          <span>
            {gap.readiness != null ? "Readiness" : "Not measured yet"}
          </span>

        </div>


        <div className="skill-gap-summary-content">

          <span>
            {previewing ? "PREVIEWING CAREER TRACK" : "TARGET ROLE"}
          </span>

          <h2>
            {gap.targetRole}
          </h2>

          <p className="skill-gap-coverage">
            Assessed {gap.assessedSkillCount} of {gap.totalRequiredSkillCount} required skills
            {gap.readiness != null && " · readiness uses assessed skills only"}
          </p>

          <p>{summaryText()}</p>

          <div className="skill-gap-summary-links">
            <Link to={`/career-tracks/${gap.trackId}`}>
              View {gap.targetRole} roadmap
              <FiArrowRight />
            </Link>
            <Link to="/career-tracks">
              <FiRefreshCw />
              Change track
            </Link>
          </div>

        </div>

      </div>


      {/* STRONG / IMPROVING / PRIORITY / NOT ASSESSED */}

      <div className="skill-gap-groups four">

        {groups.map((group) => (
          <div
            key={group.key}
            className={`skill-gap-group ${group.key}`}
          >
            <div className="skill-gap-group-head">
              {group.icon}
              <h3>{group.title}</h3>
              <span>{group.items.length}</span>
            </div>

            {group.items.length ? (
              <ul>
                {group.items.map((skill) => (
                  <li key={skill.name}>
                    {skill.name}
                    <strong>{isAssessed(skill) ? `${skill.score}%` : statusLabel[skill.status]}</strong>
                  </li>
                ))}
              </ul>
            ) : (
              <p>{group.empty}</p>
            )}
          </div>
        ))}

      </div>


      <div className="skill-gap-grid">

        <div className="skill-gap-card">

          <div className="dashboard-card-header">

            <div>
              <span>
                REQUIRED SKILLS
              </span>

              <h2>
                Skill readiness
              </h2>
            </div>

          </div>


          {skills.map((skill) => (

            <div
              className={`skill-gap-row ${statusClass[skill.status]}`}
              key={skill.name}
            >

              <div>

                <span>
                  {skill.name}
                </span>

                <strong>
                  {isAssessed(skill) ? `${skill.score}%` : "Not assessed yet"}
                </strong>

              </div>


              {isAssessed(skill) ? (
                <>
                  <div className="skill-gap-progress">
                    <div
                      style={{
                        width: `${skill.score}%`
                      }}
                    ></div>
                  </div>

                  <small className={`skill-gap-status ${statusClass[skill.status]}`}>
                    {statusLabel[skill.status]} · {skill.attempts} {skill.attempts === 1 ? "session" : "sessions"}
                  </small>
                </>
              ) : (
                <div className="skill-gap-assess">
                  <small>
                    {skill.status === "SELF_REPORTED"
                      ? "Listed in your profile — not measured by practice yet."
                      : skill.practicable
                        ? "No practice results yet."
                        : "No practice module for this skill yet."}
                  </small>
                  <Link to={skill.practiceRoute}>
                    {skill.practicable ? (
                      <>
                        <FiPlay aria-hidden="true" />
                        Assess This Skill
                      </>
                    ) : (
                      <>
                        <FiBookOpen aria-hidden="true" />
                        Study on roadmap
                      </>
                    )}
                  </Link>
                </div>
              )}

            </div>

          ))}

        </div>


        <div className="skill-gap-priority-card">

          <span className="section-tag">
            PRIORITY AREAS
          </span>

          <h2>
            What should you improve next?
          </h2>


          {focusList.length === 0 && (
            <p className="skill-gap-muted">All assessed skills are on track. Keep practising to stay sharp.</p>
          )}

          {focusList.map((skill) => (

            <div
              className="skill-gap-priority"
              key={skill.name}
            >

              <FiAlertCircle />

              <div>
                <strong>
                  {skill.name}
                </strong>

                <p>
                  {isAssessed(skill)
                    ? `Currently ${skill.score}% across ${skill.attempts} ${skill.attempts === 1 ? "session" : "sessions"}.`
                    : "Not assessed yet — a short practice set gives you a first score."}
                </p>

                <div className="skill-gap-priority-links">
                  <Link to={skill.practiceRoute}>
                    <FiPlay />
                    {isAssessed(skill) ? "Practise" : "Assess This Skill"}
                  </Link>
                </div>
              </div>

            </div>

          ))}


          {strong.length > 0 && (
            <div className="skill-gap-positive">

              <FiCheckCircle />

              <div>
                <strong>
                  Strong foundation
                </strong>

                <p>
                  {strong.map((skill) => skill.name).join(" and ")}
                  {strong.length === 1 ? " is" : " are"} currently your strongest {strong.length === 1 ? "area" : "areas"}.
                </p>
              </div>

            </div>
          )}


          <Link
            to="/my-plan"
            className="primary-button"
          >
            Build Preparation Plan
            <FiArrowRight />
          </Link>

        </div>

      </div>


      {next && (
        <Link
          to={gap.recommendedNextRoute || next.practiceRoute}
          className="skill-gap-next"
        >

          <FiTrendingUp />

          <div>

            <span>
              RECOMMENDED NEXT ACTION
            </span>

            <strong>
              {isAssessed(next) ? `Practice ${next.name}` : `Assess ${next.name}`}
            </strong>

            {gap.recommendedNextReason && (
              <small className="skill-gap-next-reason">{gap.recommendedNextReason}</small>
            )}

          </div>


          <span className="skill-gap-next-cta">
            {next.practicable ? "Start Practice" : "Open Roadmap"}
            <FiArrowRight />
          </span>

        </Link>
      )}

    </>
  );
}


/** How readiness is calculated (now and later). */
function ReadinessMethod() {
  return (
    <div className="skill-gap-method">

      <div className="skill-gap-method-head">
        <span className="section-tag">
          HOW READINESS WORKS
        </span>
        <h2>How CareerAI calculates your readiness</h2>
        <p>
          Today, readiness is the average practice score of the required skills
          you have been assessed on. Profile skills are shown but not scored.
          Assessments and interviews will be added as further signals:
        </p>
      </div>

      <div className="skill-gap-formula">

        <div>
          <FiUser />
          <strong>Profile</strong>
          <span>Skills and target role you report</span>
        </div>

        <em>+</em>

        <div>
          <FiPlay />
          <strong>Practice</strong>
          <span>Accuracy across practice topics (used now)</span>
        </div>

        <em>+</em>

        <div>
          <FiClipboard />
          <strong>Assessments</strong>
          <span>Timed mock assessment scores (later)</span>
        </div>

        <em>+</em>

        <div>
          <FiMic />
          <strong>Interviews</strong>
          <span>AI interview feedback (later)</span>
        </div>

      </div>

    </div>
  );
}


export default SkillGap;
