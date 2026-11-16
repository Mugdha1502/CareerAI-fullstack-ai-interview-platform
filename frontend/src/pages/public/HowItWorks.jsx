import { Fragment } from "react";
import { Link } from "react-router-dom";
import {
  FiUser,
  FiTarget,
  FiSearch,
  FiCalendar,
  FiBookOpen,
  FiMic,
  FiBarChart2,
  FiRefreshCw,
  FiArrowRight,
  FiCheckCircle,
  FiTrendingUp,
  FiGrid,
  FiCpu,
  FiMessageCircle
} from "react-icons/fi";

const steps = [
  {
    number: "01",
    icon: <FiUser />,
    title: "Create your profile",
    description:
      "Tell CareerAI about your current background, experience, skills and career situation.",
    path: "/register",
    action: "Create Profile"
  },
  {
    number: "02",
    icon: <FiTarget />,
    title: "Choose your career goal",
    description:
      "Select your target role and preparation goal so CareerAI understands what you are working towards.",
    path: "/career-tracks",
    action: "Explore Career Tracks"
  },
  {
    number: "03",
    icon: <FiSearch />,
    title: "Understand your readiness",
    description:
      "Use career assessment, resume analysis and skill-gap information to identify preparation priorities.",
    path: "/career/skill-gap",
    action: "View Skill Gap"
  },
  {
    number: "04",
    icon: <FiCalendar />,
    title: "Follow your preparation plan",
    description:
      "Receive structured preparation tasks covering the skills and areas that matter most for your goal.",
    path: "/my-plan",
    action: "View My Plan"
  },
  {
    number: "05",
    icon: <FiBookOpen />,
    title: "Practice your skills",
    description:
      "Work through technical questions, aptitude, coding, SQL, system design and voice-answer practice.",
    path: "/practice",
    action: "Start Practice"
  },
  {
    number: "06",
    icon: <FiMic />,
    title: "Take an AI interview",
    description:
      "Practice realistic role-based, resume-based or job-based interview conversations.",
    path: "/ai-interview",
    action: "Explore AI Interview"
  },
  {
    number: "07",
    icon: <FiBarChart2 />,
    title: "Review your performance",
    description:
      "Understand your strengths, weak areas, answer quality and improvement opportunities.",
    path: "/performance",
    action: "View Performance"
  },
  {
    number: "08",
    icon: <FiRefreshCw />,
    title: "Improve and repeat",
    description:
      "Your new performance updates future recommendations so the preparation cycle continues.",
    path: "/dashboard",
    action: "Open Dashboard"
  }
];

const outcomes = [
  {
    icon: <FiGrid />,
    title: "Personalized Dashboard",
    description:
      "See your career readiness, progress and recommended next actions."
  },
  {
    icon: <FiCalendar />,
    title: "Daily Preparation Plan",
    description:
      "Receive focused tasks based on your role, skills and available time."
  },
  {
    icon: <FiCpu />,
    title: "Skill Intelligence",
    description:
      "Understand stronger skills, weaker areas and preparation priorities."
  },
  {
    icon: <FiMessageCircle />,
    title: "Continuous Feedback",
    description:
      "Practice and interview results influence future recommendations."
  }
];

const loopItems = [
  { icon: <FiUser />, label: "User Profile", path: "/register" },
  { icon: <FiBookOpen />, label: "Practice", path: "/practice" },
  { icon: <FiMic />, label: "Interview", path: "/ai-interview" },
  { icon: <FiBarChart2 />, label: "Performance", path: "/performance" },
  {
    icon: <FiTarget />,
    label: "Recommendation",
    path: "/my-plan",
    highlighted: true
  }
];

const exampleSkills = [
  { name: "Java", score: 88, status: "Java score is strong" },
  { name: "SQL", score: 84, status: "SQL score is strong" },
  { name: "Spring Boot", score: 72, status: "Spring Boot is improving" },
  {
    name: "Spring Security",
    score: 54,
    status: "Spring Security is currently weaker",
    warning: true
  }
];

function HowItWorks() {
  return (
    <div className="how-it-works-page">

      {/* HERO */}

      <section className="inner-page-hero">

        <div className="section-container inner-page-hero-content">

          <span className="section-tag">
            HOW CAREERAI WORKS
          </span>

          <h1>
            A connected journey from career goal to interview readiness
          </h1>

          <p>
            CareerAI does not treat preparation as separate disconnected
            activities. Your profile, practice results, resume,
            interviews and progress work together to guide what you
            should prepare next.
          </p>

          <div className="inner-page-actions">

            <Link
              to="/register"
              className="primary-button"
            >
              Start Your Journey
              <FiArrowRight />
            </Link>

            <Link
              to="/features"
              className="secondary-button"
            >
              Explore Features
            </Link>

          </div>

        </div>

      </section>


      {/* EIGHT-STEP JOURNEY */}

      <section className="home-section how-journey-section">

        <div className="section-container">

          <header className="section-heading centered">

            <span className="section-tag">
              YOUR CAREERAI JOURNEY
            </span>

            <h2>
              Eight steps designed around continuous improvement
            </h2>

            <p>
              Every stage helps CareerAI better understand your
              preparation and recommend what should come next.
            </p>

          </header>


          <div className="home-journey-grid how-journey-grid">

            {steps.map((step) => (
              <Link
                to={step.path}
                className="home-journey-card how-journey-card"
                key={step.number}
              >

                <div className="home-journey-top">

                  <div className="home-journey-number">
                    {step.number}
                  </div>

                  <div className="home-journey-icon">
                    {step.icon}
                  </div>

                </div>

                <h3>{step.title}</h3>

                <p>{step.description}</p>

                <span>
                  {step.action}
                  <FiArrowRight />
                </span>

              </Link>
            ))}

          </div>

        </div>

      </section>


      {/* WHAT CAREERAI BUILDS FOR YOU */}

      <section className="home-section alternate-section how-outcomes-section">

        <div className="section-container">

          <header className="section-heading centered">

            <span className="section-tag">
              WHAT CAREERAI BUILDS FOR YOU
            </span>

            <h2>
              Your preparation, organised into one connected workspace
            </h2>

            <p>
              As you move through the journey, CareerAI turns your
              activity into tools that keep your preparation focused.
            </p>

          </header>


          <div className="home-journey-grid how-outcomes-grid">

            {outcomes.map((outcome) => (
              <article
                className="feature-card how-outcome-card"
                key={outcome.title}
              >

                <div className="feature-icon">
                  {outcome.icon}
                </div>

                <h3>{outcome.title}</h3>

                <p>{outcome.description}</p>

              </article>
            ))}

          </div>

        </div>

      </section>


      {/* PERSONALIZATION LOOP */}

      <section className="home-section how-data-section">

        <div className="section-container">

          <header className="section-heading centered">

            <span className="section-tag">
              PERSONALIZATION LOOP
            </span>

            <h2>
              Your results influence what CareerAI recommends next
            </h2>

            <p>
              CareerAI is designed around a feedback loop rather than
              displaying the same preparation content to every user.
            </p>

          </header>


          <nav
            className="careerai-loop"
            aria-label="CareerAI personalization loop"
          >

            {loopItems.map((item, index) => (
              <Fragment key={item.label}>

                <Link
                  to={item.path}
                  className={`loop-item${item.highlighted ? " highlighted" : ""}`}
                >
                  {item.icon}
                  <span>{item.label}</span>
                </Link>

                {index !== loopItems.length - 1 && (
                  <div
                    className="loop-arrow"
                    aria-hidden="true"
                  >
                    <FiArrowRight />
                  </div>
                )}

              </Fragment>
            ))}

          </nav>


          <div className="loop-return">

            <FiRefreshCw aria-hidden="true" />

            <span>
              Updated recommendations lead to new practice and new results
            </span>

          </div>

        </div>

      </section>


      {/* EXAMPLE */}

      <section className="home-section">

        <div className="section-container split-section">

          <div className="split-content">

            <span className="section-tag">
              EXAMPLE
            </span>

            <h2>
              CareerAI recommendations change with your performance
            </h2>

            <p>
              Suppose your target role is Java Full Stack Developer.
              Your recent results show strong Java and SQL performance,
              but Spring Security still needs improvement.
            </p>

            <ul className="check-list">

              {exampleSkills.map((skill) => (
                <li key={skill.name}>
                  <FiCheckCircle />
                  {skill.status}
                </li>
              ))}

            </ul>

            <p>
              Instead of recommending another general Java session,
              CareerAI can prioritize Spring Security in your plan,
              dashboard and practice recommendations.
            </p>

          </div>


          <aside className="recommendation-example-card">

            <div className="recommendation-example-header">

              <div>
                <span>TARGET ROLE</span>
                <h3>Java Full Stack Developer</h3>
              </div>

              <FiTrendingUp aria-hidden="true" />

            </div>

            {exampleSkills.map((skill) => (
              <div
                className={`example-skill${skill.warning ? " warning" : ""}`}
                key={skill.name}
              >

                <div>
                  <span>{skill.name}</span>
                  <strong>{skill.score}%</strong>
                </div>

                <div
                  className="example-bar"
                  role="progressbar"
                  aria-label={`${skill.name} score`}
                  aria-valuenow={skill.score}
                  aria-valuemin={0}
                  aria-valuemax={100}
                >
                  <div style={{ width: `${skill.score}%` }}></div>
                </div>

              </div>
            ))}


            <div className="example-next-step">

              <FiTarget />

              <div>
                <span>Recommended next step</span>
                <strong>Spring Security Practice</strong>
                <p>
                  Review authentication, authorization and JWT concepts.
                </p>
              </div>

            </div>


            <div className="inner-page-actions how-example-actions">

              <Link
                to="/practice/technical"
                className="primary-button"
              >
                Practice Spring Security
                <FiArrowRight />
              </Link>

              <Link
                to="/career/skill-gap"
                className="secondary-button"
              >
                View Full Skill Gap
              </Link>

            </div>

          </aside>

        </div>

      </section>


      {/* FINAL CTA */}

      <section className="how-final-section">

        <div className="section-container">

          <div className="how-final-card">

            <span className="section-tag light">
              CONTINUOUS IMPROVEMENT
            </span>

            <h2>
              Prepare. Practice. Interview. Improve.
            </h2>

            <p>
              CareerAI is designed to turn every practice session and
              interview into information that helps guide your next
              preparation step.
            </p>

            <div className="home-final-actions">

              <Link
                to="/register"
                className="cta-white-button"
              >
                Create Your CareerAI Account
                <FiArrowRight />
              </Link>

              <Link
                to="/career-tracks"
                className="home-guest-link"
              >
                Explore Career Tracks
              </Link>

            </div>

          </div>

        </div>

      </section>

    </div>
  );
}

export default HowItWorks;
