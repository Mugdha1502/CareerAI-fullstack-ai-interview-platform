import { Link } from "react-router-dom";

import {
  FiBookOpen,
  FiCode,
  FiDatabase,
  FiGrid,
  FiMic,
  FiTarget,
  FiClock,
  FiZap,
  FiArrowRight,
  FiCheckCircle,
  FiPlay,
  FiTrendingUp,
  FiBarChart2
} from "react-icons/fi";

function Practice() {
  const modules = [
    {
      id: "technical",
      icon: <FiBookOpen />,
      title: "Technical Practice",
      description:
        "Prepare programming languages, frameworks, databases and core computer science subjects.",
      path: "/practice/technical",
      activity: "Notes • Quiz • Descriptive"
    },
    {
      id: "aptitude",
      icon: <FiGrid />,
      title: "Aptitude",
      description:
        "Prepare quantitative, logical, verbal and data interpretation questions.",
      path: "/practice/aptitude",
      activity: "MCQ • Timed Practice"
    },
    {
      id: "coding",
      icon: <FiCode />,
      title: "Coding",
      description:
        "Solve programming problems using multiple supported languages.",
      path: "/practice/coding",
      activity: "Problems • Code Workspace"
    },
    {
      id: "sql",
      icon: <FiDatabase />,
      title: "SQL Practice",
      description:
        "Write SQL queries using safe controlled practice datasets.",
      path: "/practice/sql",
      activity: "Queries • Practice Dataset"
    },
    {
      id: "system-design",
      icon: <FiTarget />,
      title: "System Design",
      description:
        "Learn how to approach system-design interview problems.",
      path: "/practice/system-design",
      activity: "Architecture • Written Practice"
    },
    {
      id: "voice",
      icon: <FiMic />,
      title: "Voice Practice",
      description:
        "Practise explaining technical, HR and behavioural answers aloud.",
      path: "/practice/voice",
      activity: "Communication • Answer Practice"
    },
    {
      id: "daily-challenge",
      icon: <FiZap />,
      title: "Daily Challenge",
      description:
        "Complete a short daily challenge and maintain your preparation streak.",
      path: "/practice/daily-challenge",
      activity: "Daily Mixed Challenge"
    },
    {
      id: "mock-assessment",
      icon: <FiClock />,
      title: "Mock Assessments",
      description:
        "Take timed recruitment-style assessments across multiple skill areas.",
      path: "/practice/mock-assessment",
      activity: "Timed Recruitment Test"
    }
  ];

  return (
    <div className="practice-page">

      <section className="practice-hero">

        <div className="practice-hero-glow practice-glow-one"></div>
        <div className="practice-hero-glow practice-glow-two"></div>

        <div className="section-container practice-hero-grid">

          <div className="practice-hero-content">

            <span className="section-tag">
              CAREERAI PRACTICE
            </span>

            <h1>
              Practice the skills that
              <span> interviews actually test.</span>
            </h1>

            <p>
              Prepare technical subjects, aptitude, coding, SQL, system
              design and communication from one connected practice
              workspace.
            </p>

            <div className="practice-hero-actions">

              <a
                href="#practice-modules"
                className="primary-button"
              >
                Start Practising
                <FiArrowRight />
              </a>

              <Link
                to="/ai-interview"
                className="secondary-button"
              >
                <FiMic />
                Try AI Interview
              </Link>

            </div>

            <div className="practice-hero-points">

              <span>
                <FiCheckCircle />
                Multiple skill areas
              </span>

              <span>
                <FiCheckCircle />
                Difficulty levels
              </span>

              <span>
                <FiCheckCircle />
                Progress tracking
              </span>

            </div>

          </div>


          <div className="practice-dashboard-preview">

            <div className="practice-preview-header">

              <div>
                <span>TODAY'S PRACTICE</span>
                <h3>Preparation Overview</h3>
              </div>

              <div className="practice-streak">
                <FiZap />
                7
                <span>day streak</span>
              </div>

            </div>


            <div className="practice-task active">

              <div className="practice-task-icon">
                <FiBookOpen />
              </div>

              <div>
                <span>Technical</span>
                <strong>Spring Security</strong>
                <p>5 questions remaining</p>
              </div>

              <Link to="/practice/technical">
                <FiPlay />
              </Link>

            </div>


            <div className="practice-task">

              <div className="practice-task-icon">
                <FiDatabase />
              </div>

              <div>
                <span>SQL</span>
                <strong>JOIN Practice</strong>
                <p>3 problems remaining</p>
              </div>

              <Link to="/practice/sql">
                <FiPlay />
              </Link>

            </div>


            <div className="practice-progress">

              <div>
                <span>Today's progress</span>
                <strong>65%</strong>
              </div>

              <div className="practice-progress-bar">
                <div style={{ width: "65%" }}></div>
              </div>

            </div>

          </div>

        </div>

      </section>


      <section
        className="home-section"
        id="practice-modules"
      >

        <div className="section-container">

          <div className="section-heading centered">

            <span className="section-tag">
              PRACTICE MODULES
            </span>

            <h2>
              Choose what you want to practise
            </h2>

            <p>
              Open a dedicated practice workspace for each preparation area.
            </p>

          </div>


          <div className="practice-module-grid">

            {modules.map((module) => (
              <Link
                key={module.id}
                to={module.path}
                className="practice-module-card"
              >

                <div className="practice-module-icon">
                  {module.icon}
                </div>

                <span className="practice-module-activity">
                  {module.activity}
                </span>

                <h3>{module.title}</h3>

                <p>{module.description}</p>

                <span className="practice-module-open">
                  Open Module
                  <FiArrowRight />
                </span>

              </Link>
            ))}

          </div>

        </div>

      </section>


      <section className="home-section">

        <div className="section-container">

          <div className="section-heading centered">

            <span className="section-tag">
              FLEXIBLE PRACTICE
            </span>

            <h2>
              Practice differently depending on your goal
            </h2>

          </div>


          <div className="practice-mode-grid">

            <div className="practice-mode-card">

              <FiBookOpen />

              <h3>Practice Mode</h3>

              <p>
                Work at your own pace and receive explanations while practising.
              </p>

              <ul>
                <li>
                  <FiCheckCircle />
                  Immediate feedback
                </li>

                <li>
                  <FiCheckCircle />
                  Topic-based learning
                </li>

                <li>
                  <FiCheckCircle />
                  Multiple difficulty levels
                </li>
              </ul>

            </div>


            <div className="practice-mode-card highlighted">

              <FiClock />

              <h3>Mock Test Mode</h3>

              <p>
                Simulate recruitment assessments with timers and final scores.
              </p>

              <ul>
                <li>
                  <FiCheckCircle />
                  Timed tests
                </li>

                <li>
                  <FiCheckCircle />
                  Final score
                </li>

                <li>
                  <FiCheckCircle />
                  Performance breakdown
                </li>
              </ul>

            </div>


            <div className="practice-mode-card">

              <FiTrendingUp />

              <h3>Adaptive Practice</h3>

              <p>
                Future questions can adapt based on previous performance.
              </p>

              <ul>
                <li>
                  <FiCheckCircle />
                  Weak-area focus
                </li>

                <li>
                  <FiCheckCircle />
                  Personalized difficulty
                </li>

                <li>
                  <FiCheckCircle />
                  Smarter recommendations
                </li>
              </ul>

            </div>

          </div>

        </div>

      </section>


      <section className="home-section practice-performance-section">

        <div className="section-container split-section">

          <div className="split-content">

            <span className="section-tag">
              PERFORMANCE
            </span>

            <h2>
              Practice results should influence what you prepare next
            </h2>

            <p>
              CareerAI can use practice activity to understand stronger
              skills and weaker areas.
            </p>

            <ul className="check-list">

              <li>
                <FiCheckCircle />
                Track attempts
              </li>

              <li>
                <FiCheckCircle />
                Compare subjects
              </li>

              <li>
                <FiCheckCircle />
                Identify weak topics
              </li>

              <li>
                <FiCheckCircle />
                Update preparation recommendations
              </li>

            </ul>

          </div>


          <div className="practice-performance-card">

            <div className="practice-performance-header">

              <div>
                <span>SKILL PERFORMANCE</span>
                <h3>Current preparation</h3>
              </div>

              <FiBarChart2 />

            </div>

            <PerformanceRow label="Java" value={88} />
            <PerformanceRow label="SQL" value={84} />
            <PerformanceRow label="React" value={76} />
            <PerformanceRow label="Spring Boot" value={72} />
            <PerformanceRow label="Spring Security" value={54} warning />

          </div>

        </div>

      </section>


      <section className="practice-cta-section">

        <div className="section-container">

          <div className="practice-cta">

            <div>

              <span className="section-tag light">
                START PRACTISING
              </span>

              <h2>
                Build stronger skills one session at a time.
              </h2>

              <p>
                Create your CareerAI profile and prepare around the role
                you actually want.
              </p>

            </div>

            <Link
              to="/register"
              className="cta-white-button"
            >
              Start Preparing
              <FiArrowRight />
            </Link>

          </div>

        </div>

      </section>

    </div>
  );
}


function PerformanceRow({ label, value, warning }) {
  return (
    <div className="practice-performance-row">

      <div>
        <span>{label}</span>
        <strong>{value}%</strong>
      </div>

      <div className="practice-performance-bar">
        <div
          className={warning ? "warning" : ""}
          style={{ width: `${value}%` }}
        ></div>
      </div>

    </div>
  );
}

export default Practice;