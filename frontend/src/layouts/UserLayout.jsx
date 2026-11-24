import {
  useEffect,
  useState
} from "react";

import {
  Link,
  NavLink,
  Outlet,
  useNavigate
} from "react-router-dom";

import {
  FiHome,
  FiTarget,
  FiBookOpen,
  FiCode,
  FiDatabase,
  FiMic,
  FiFileText,
  FiBarChart2,
  FiMessageCircle,
  FiBriefcase,
  FiAward,
  FiUser,
  FiSettings,
  FiLogOut,
  FiMenu,
  FiX,
  FiZap,
  FiBell,
  FiCreditCard
} from "react-icons/fi";

import {
  useAuth
} from "../context/auth";

import PlanBadge from "../components/common/PlanBadge";
import { useSubscription } from "../context/subscription";
import { formatDate } from "../services/billingService";

import {
  getXP,
  getLevelInfo,
  getStreak
} from "../services/xpService";

import {
  getSettings,
  SETTINGS_EVENT
} from "../services/settingsService";


function UserLayout() {

  const navigate =
    useNavigate();


  const {
    user,
    logout,
    isRegistered
  } = useAuth();

  // Plan shown in the shell comes from the verified backend subscription (never from a checkout callback).
  const {
    subscription,
    isPro,
    displayPlan
  } = useSubscription();


  const [sidebarOpen,
    setSidebarOpen] =
    useState(false);


  const [display,
    setDisplay] =
    useState(() =>
      getSettings(user?.id)
    );


  // Settings page broadcasts changes so display preferences apply immediately.
  useEffect(() => {

    const handleChange =
      (event) =>
        setDisplay(event.detail);

    window.addEventListener(
      SETTINGS_EVENT,
      handleChange
    );

    return () =>
      window.removeEventListener(
        SETTINGS_EVENT,
        handleChange
      );

  }, []);


  // Escape closes the mobile sidebar.
  useEffect(() => {

    if (!sidebarOpen) {
      return undefined;
    }

    const handleKey =
      (event) => {
        if (event.key === "Escape") {
          setSidebarOpen(false);
        }
      };

    window.addEventListener(
      "keydown",
      handleKey
    );

    return () =>
      window.removeEventListener(
        "keydown",
        handleKey
      );

  }, [sidebarOpen]);


  const xp =
    getXP(
      user?.id
    );


  const level =
    getLevelInfo(xp);


  const streak =
    getStreak(
      user?.id
    );


  const closeSidebar =
    () =>
      setSidebarOpen(false);


  const handleLogout =
    () => {

      logout();

      navigate("/");

    };


  const shellClasses = [
    "app-shell",
    display.reduceMotion ? "reduce-motion" : "",
    display.compactLayout ? "compact" : ""
  ].filter(Boolean).join(" ");


  return (
    <div className={shellClasses}>

      <aside
        id="app-sidebar"
        className={
          `app-sidebar ${
            sidebarOpen
              ? "open"
              : ""
          }`
        }
      >

        <div className="sidebar-header">

          <Link
            to="/dashboard"
            className="sidebar-brand"
            onClick={closeSidebar}
          >
            <div>C</div>

            <span>
              Career<strong>AI</strong>
            </span>
          </Link>


          <button
            type="button"
            className="sidebar-close"
            onClick={closeSidebar}
            aria-label="Close menu"
          >
            <FiX aria-hidden="true" />
          </button>

        </div>


        <div className="sidebar-user-mini">

          <div className="sidebar-user-avatar">
            {user?.name
              ?.charAt(0)
              ?.toUpperCase() ||
              "G"}
          </div>

          <div>

            <strong>
              {user?.name}
            </strong>

            <PlanBadge
              plan={displayPlan}
            />

          </div>

        </div>


        <nav
          className="sidebar-nav"
          aria-label="CareerAI workspace"
        >

          <SidebarLabel>
            OVERVIEW
          </SidebarLabel>

          <SideLink
            to="/dashboard"
            icon={<FiHome />}
            label="Dashboard"
            onNavigate={closeSidebar}
          />

          <SideLink
            to="/my-plan"
            icon={<FiTarget />}
            label="My Plan"
            onNavigate={closeSidebar}
          />


          <SidebarLabel>
            PREPARE
          </SidebarLabel>

          <SideLink
            to="/practice"
            end
            icon={<FiBookOpen />}
            label="Practice"
            onNavigate={closeSidebar}
          />

          <SideLink
            to="/practice/coding"
            icon={<FiCode />}
            label="Coding"
            onNavigate={closeSidebar}
          />

          <SideLink
            to="/practice/sql"
            icon={<FiDatabase />}
            label="SQL Practice"
            onNavigate={closeSidebar}
          />


          <SidebarLabel>
            INTERVIEW
          </SidebarLabel>

          <SideLink
            to="/ai-interview"
            icon={<FiMic />}
            label="AI Interview"
            onNavigate={closeSidebar}
          />


          <SidebarLabel>
            CAREER TOOLS
          </SidebarLabel>

          <SideLink
            to="/resume-tools"
            icon={<FiFileText />}
            label="Resume"
            onNavigate={closeSidebar}
          />

          <SideLink
            to="/applications"
            icon={<FiBriefcase />}
            label="Applications"
            onNavigate={closeSidebar}
          />


          <SidebarLabel>
            INSIGHTS
          </SidebarLabel>

          <SideLink
            to="/performance"
            icon={<FiBarChart2 />}
            label="Performance"
            onNavigate={closeSidebar}
          />

          <SideLink
            to="/coach"
            icon={<FiMessageCircle />}
            label="AI Coach"
            onNavigate={closeSidebar}
          />

          <SideLink
            to="/achievements"
            icon={<FiAward />}
            label="Achievements"
            onNavigate={closeSidebar}
          />


          <SidebarLabel>
            ACCOUNT
          </SidebarLabel>

          <SideLink
            to="/profile"
            icon={<FiUser />}
            label="Profile"
            onNavigate={closeSidebar}
          />

          <SideLink
            to="/settings"
            icon={<FiSettings />}
            label="Settings"
            onNavigate={closeSidebar}
          />

          <SideLink
            to="/pricing"
            icon={<FiCreditCard />}
            label="Plans"
            onNavigate={closeSidebar}
          />

          {isRegistered && (
            <SideLink
              to="/settings/billing"
              icon={<FiCreditCard />}
              label="Billing"
              onNavigate={closeSidebar}
            />
          )}

        </nav>


        <div className="sidebar-plan-card">

          <div>

            <PlanBadge
              plan={displayPlan}
            />

            <strong>
              Your Plan
            </strong>

          </div>

          <p>
            {!isRegistered
              ? "Create an account to save your progress."
              : isPro
                ? `PRO access until ${formatDate(subscription?.expiresAt)}`
                : "Upgrade for higher CareerAI limits."}
          </p>

          <Link
            to={
              !isRegistered
                ? "/register"
                : isPro
                  ? "/settings/billing"
                  : "/pricing"
            }
            onClick={closeSidebar}
          >
            {!isRegistered
              ? "Create Account"
              : isPro
                ? "View Plan"
                : "Upgrade"}
          </Link>

        </div>


        <button
          type="button"
          className="sidebar-logout"
          onClick={
            handleLogout
          }
        >
          <FiLogOut aria-hidden="true" />
          Log Out
        </button>

      </aside>


      {sidebarOpen && (
        <button
          type="button"
          className="app-sidebar-backdrop"
          onClick={closeSidebar}
          aria-label="Close menu"
          tabIndex={-1}
        />
      )}


      <div className="app-main">

        <header className="app-topbar">

          <button
            type="button"
            className="mobile-menu-button"
            onClick={() =>
              setSidebarOpen(true)
            }
            aria-label="Open menu"
            aria-controls="app-sidebar"
            aria-expanded={sidebarOpen}
          >
            <FiMenu aria-hidden="true" />
          </button>


          <div className="topbar-spacer"></div>


          <div className="topbar-stats">

            <div>
              <FiZap aria-hidden="true" />
              {streak.current}
              <span>day streak</span>
            </div>

            <Link
              to="/achievements"
              className="topbar-xp-link"
              aria-label={`Level ${level.level}, ${xp} XP. View achievements`}
            >
              <FiAward aria-hidden="true" />
              Lv {level.level} · {xp} XP
            </Link>

          </div>


          <Link
            to="/settings"
            className="topbar-icon-button"
            aria-label="Notification settings"
            title="Notification settings"
          >
            <FiBell aria-hidden="true" />
          </Link>


          <Link
            to="/profile"
            className="topbar-profile"
            aria-label="Your profile"
          >

            <div aria-hidden="true">
              {user?.name
                ?.charAt(0)
                ?.toUpperCase() ||
                "G"}
            </div>

            <span>
              {user?.name}
            </span>

          </Link>

        </header>


        <main className="app-content">

          <Outlet />

        </main>

      </div>

    </div>
  );
}


function SidebarLabel({
  children
}) {

  return (
    <span className="sidebar-label">
      {children}
    </span>
  );
}


function SideLink({
  to,
  end,
  icon,
  label,
  onNavigate
}) {

  return (
    <NavLink
      to={to}
      end={end}
      onClick={onNavigate}
      className={({
        isActive
      }) =>
        `sidebar-link ${
          isActive
            ? "active"
            : ""
        }`
      }
    >
      {icon}
      <span>{label}</span>
    </NavLink>
  );
}


export default UserLayout;
