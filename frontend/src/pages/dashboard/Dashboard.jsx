import {
  useEffect,
  useState
} from "react";

import {
  getToken
} from "../../services/api";

import {
  getMyPlan
} from "../../services/planService";

import {
  Link
} from "react-router-dom";

import {
  FiArrowRight,
  FiAward,
  FiBarChart2,
  FiBookOpen,
  FiBriefcase,
  FiCheckCircle,
  FiDatabase,
  FiFileText,
  FiMic,
  FiTarget,
  FiTrendingUp,
  FiZap
} from "react-icons/fi";

import {
  useAuth
} from "../../context/AuthContext";

import PlanBadge from "../../components/common/PlanBadge";
import { useSubscription } from "../../context/subscription";

import PlanUsageCard from "../../components/billing/PlanUsageCard";

import {
  getXP,
  getLevelInfo,
  getStreak,
  recordDailyActivity
} from "../../services/xpService";

import {
  resolveActiveTrack
} from "../career/careerTrackData";

import {
  buildDailyTasks,
  getPrioritySkills,
  getTaskStatuses
} from "../plan/planData";


function Dashboard() {

  const {
    user,
    isGuest
  } = useAuth();

  const { displayPlan } = useSubscription();


  // Visiting the dashboard counts as today's activity (idempotent per day).
  const [streak] =
    useState(() =>
      user?.id
        ? recordDailyActivity(user.id)
        : getStreak(null)
    );


  // Today's plan and recommendation: the backend plan (GET /api/plan/me) for
  // signed-in users, the local demo plan for guests.
  const [backendPlan,
    setBackendPlan] =
    useState(null);

  useEffect(() => {

    if (!getToken()) {
      return undefined;
    }

    let cancelled = false;

    getMyPlan()
      .then((plan) => {
        if (!cancelled) setBackendPlan(plan);
      })
      .catch(() => {
        // Keep the dashboard usable; My Plan shows the detailed error.
      });

    return () => {
      cancelled = true;
    };

  }, []);


  const track =
    resolveActiveTrack(user);

  const taskStatuses =
    getTaskStatuses(user?.id);

  const planTasks =
    backendPlan
      ? backendPlan.tasks.map((task) => ({
          id: task.id,
          title: task.title,
          category: task.type.replace("_", " ").toLowerCase().replace(/^\w/, (c) => c.toUpperCase()),
          minutes: task.durationMinutes,
          completed: task.completed
        }))
      : buildDailyTasks(track).map((task) => ({
          ...task,
          completed: taskStatuses[task.id] === "completed"
        }));

  const completedTasks =
    planTasks.filter(
      (task) => task.completed
    ).length;

  const planProgress =
    planTasks.length
      ? Math.round(
          (completedTasks /
            planTasks.length) *
            100
        )
      : 0;

  const focusSkill =
    getPrioritySkills(track, 1)[0];

  const nextAction =
    backendPlan?.recommendedNextAction;


  const xp =
    getXP(
      user?.id
    );


  const level =
    getLevelInfo(xp);


  const targetRole =
    user?.profile
      ?.targetRole ||
    "Explore Career Tracks";


  return (
    <div className="dashboard-page">

      <section className="dashboard-welcome">

        <div>

          <span className="dashboard-eyebrow">
            CAREERAI DASHBOARD
          </span>

          <h1>
            Welcome back,{" "}
            {user?.name}.
          </h1>

          <p>
            {isGuest
              ? "Explore CareerAI and see how your preparation could look."
              : `Continue preparing for ${targetRole}.`}
          </p>

        </div>


        <div className="dashboard-welcome-actions">

          <PlanBadge
            plan={displayPlan}
          />

          <Link
            to="/practice"
            className="primary-button"
          >
            Start Practice
            <FiArrowRight />
          </Link>

        </div>

      </section>


      {isGuest && (

        <div className="guest-dashboard-banner">

          <div>

            <FiZap />

            <div>

              <strong>
                You're exploring CareerAI as a guest.
              </strong>

              <p>
                Create a free account to save your XP,
                progress, interviews and career plan.
              </p>

            </div>

          </div>


          <Link
            to="/register"
            className="primary-button"
          >
            Save My Progress
          </Link>

        </div>

      )}


      <section className="xp-dashboard-card">

        <div className="xp-level-circle">
          {level.level}
        </div>


        <div className="xp-dashboard-main">

          <div className="xp-dashboard-header">

            <div>

              <span>
                LEVEL {level.level}
              </span>

              <h2>
                {level.name}
              </h2>

            </div>

            <strong>
              {xp} XP
            </strong>

          </div>


          <div className="xp-progress-bar">

            <div
              style={{
                width:
                  `${level.progress}%`
              }}
            ></div>

          </div>


          <p>

            {level.remainingXP > 0
              ? `${level.remainingXP} XP until Level ${level.nextLevel}`
              : "Maximum level reached"}

            {" · "}

            <Link to="/achievements">
              View achievements
            </Link>

          </p>

        </div>


        <div className="streak-card">

          <FiZap />

          <strong>
            {streak.current}
          </strong>

          <span>
            Day Streak
          </span>

        </div>

      </section>


      <section className="dashboard-stat-grid">

        <DashboardStat
          icon={<FiTarget />}
          label="Career Readiness"
          value="68%"
          note={targetRole}
        />

        <DashboardStat
          icon={<FiFileText />}
          label="Resume Readiness"
          value="82%"
          note="Good foundation"
        />

        <DashboardStat
          icon={<FiMic />}
          label="Interview Score"
          value="84%"
          note="Last interview"
        />

        <DashboardStat
          icon={<FiBarChart2 />}
          label="Practice Accuracy"
          value="78%"
          note="Last 30 days"
        />

      </section>


      <section className="dashboard-two-column">

        <div className="dashboard-card">

          <div className="dashboard-card-header">

            <div>
              <span>TODAY'S PLAN</span>
              <h2>
                Continue your preparation
              </h2>
            </div>

            <Link to="/my-plan">
              View Plan
            </Link>

          </div>


          {planTasks.slice(0, 4).map(
            (task) => (

              <Task
                key={task.id}
                completed={
                  task.completed
                }
                title={task.title}
                meta={task.category}
                xp={`${task.minutes} min`}
              />

            )
          )}


          <div className="today-plan-progress">

            <div>
              <span>
                Today's progress · {completedTasks} / {planTasks.length} tasks
              </span>

              <strong>
                {planProgress}%
              </strong>
            </div>

            <div>
              <span
                style={{
                  width: `${planProgress}%`
                }}
              ></span>
            </div>

          </div>

        </div>


        <div className="dashboard-card recommendation-card-dashboard">

          <span className="dashboard-card-label">
            RECOMMENDED FOR YOU
          </span>

          <div className="recommendation-large-icon">
            <FiTrendingUp />
          </div>

          {backendPlan ? (
            <>
              <h2>
                {nextAction ? nextAction.title : "Today's plan is complete"}
              </h2>

              <p>
                {nextAction
                  ? nextAction.description
                  : "Review your skill gap to see what to focus on next."}
              </p>
            </>
          ) : (
            <>
              <h2>
                Improve {focusSkill.name}
              </h2>

              <p>
                {focusSkill.name} is your top priority skill
                for {track.title} in the sample data
                ({focusSkill.value}%).
              </p>
            </>
          )}

          <Link
            to={
              backendPlan
                ? nextAction?.route || "/career/skill-gap"
                : focusSkill.practicePath ||
                  focusSkill.topicPath ||
                  "/practice"
            }
            className="primary-button"
          >
            Practice Now
            <FiArrowRight />
          </Link>

        </div>

      </section>


      <section className="dashboard-two-column">

        <div className="dashboard-card">

          <div className="dashboard-card-header">

            <div>
              <span>SKILL PERFORMANCE</span>
              <h2>
                Current strengths
              </h2>
            </div>

            <Link to="/performance">
              View Performance
            </Link>

          </div>

          <Skill
            name="Java"
            value={88}
          />

          <Skill
            name="SQL"
            value={84}
          />

          <Skill
            name="React"
            value={76}
          />

          <Skill
            name="Spring Boot"
            value={72}
          />

          <Skill
            name="Spring Security"
            value={54}
            warning
          />

        </div>


        <div className="dashboard-card">

          <div className="dashboard-card-header">

            <div>
              <span>DAILY CHALLENGE</span>
              <h2>
                SQL • Intermediate
              </h2>
            </div>

            <FiDatabase />
          </div>


          <div className="daily-challenge-body">

            <div className="daily-xp">
              <FiZap />
              +20 XP
            </div>

            <h3>
              JOIN Challenge
            </h3>

            <p>
              Complete today's SQL challenge and
              continue your preparation streak.
            </p>

            <Link
              to="/practice/daily-challenge"
              className="primary-button"
            >
              Start Challenge
              <FiArrowRight />
            </Link>

          </div>

        </div>

      </section>


      {/* Plan + usage from the backend (GET /api/billing/me). */}
      <PlanUsageCard />


      <section className="quick-action-section">

        <div className="dashboard-card-header">

          <div>
            <span>QUICK ACTIONS</span>
            <h2>
              What do you want to do?
            </h2>
          </div>

        </div>


        <div className="dashboard-quick-grid">

          <QuickAction
            to="/practice"
            icon={<FiBookOpen />}
            title="Practice"
          />

          <QuickAction
            to="/my-plan"
            icon={<FiCheckCircle />}
            title="My Plan"
          />

          <QuickAction
            to="/performance"
            icon={<FiBarChart2 />}
            title="Performance"
          />

          <QuickAction
            to="/ai-interview"
            icon={<FiMic />}
            title="AI Interview"
          />

          <QuickAction
            to="/resume-tools"
            icon={<FiFileText />}
            title="Resume"
          />

          <QuickAction
            to={`/career-tracks/${track.id}`}
            icon={<FiTarget />}
            title="Career Track"
          />

          <QuickAction
            to="/applications"
            icon={<FiBriefcase />}
            title="Applications"
          />

          <QuickAction
            to="/achievements"
            icon={<FiAward />}
            title="Achievements"
          />

        </div>

      </section>

    </div>
  );
}


function DashboardStat({
  icon,
  label,
  value,
  note
}) {

  return (
    <div className="dashboard-stat-card">

      <div className="dashboard-stat-icon">
        {icon}
      </div>

      <span>{label}</span>

      <strong>{value}</strong>

      <small>{note}</small>

    </div>
  );
}


function Task({
  completed,
  title,
  meta,
  xp
}) {

  return (
    <div
      className={`dashboard-task ${
        completed
          ? "completed"
          : ""
      }`}
    >

      <div className="dashboard-task-check">
        {completed &&
          <FiCheckCircle />}
      </div>

      <div>

        <strong>
          {title}
        </strong>

        <span>
          {meta}
        </span>

      </div>

      <b>{xp}</b>

    </div>
  );
}


function Skill({
  name,
  value,
  warning
}) {

  return (
    <div className="dashboard-skill">

      <div>
        <span>{name}</span>
        <strong>
          {value}%
        </strong>
      </div>

      <div className="dashboard-skill-bar">

        <span
          className={
            warning
              ? "warning"
              : ""
          }
          style={{
            width:
              `${value}%`
          }}
        ></span>

      </div>

    </div>
  );
}




function QuickAction({
  to,
  icon,
  title
}) {

  return (
    <Link
      to={to}
      className="dashboard-quick-action"
    >
      <div>{icon}</div>

      <strong>
        {title}
      </strong>

      <FiArrowRight />
    </Link>
  );
}


export default Dashboard;