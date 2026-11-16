import { Link } from "react-router-dom";

import {
  FiArrowRight,
  FiArrowDown,
  FiCompass,
  FiBookOpen,
  FiGrid,
  FiCode,
  FiDatabase,
  FiFileText,
  FiMic,
  FiTarget,
  FiBarChart2,
  FiUser,
  FiShuffle,
  FiLink,
  FiTrendingUp,
  FiRefreshCw,
  FiSliders,
  FiFlag,
  FiPlay,
  FiShield,
  FiCheckCircle,
  FiLinkedin,
  FiGithub,
  FiMail
} from "react-icons/fi";

const capabilities = [
  { icon: <FiCompass />, label: "Career planning" },
  { icon: <FiBookOpen />, label: "Technical practice" },
  { icon: <FiGrid />, label: "Aptitude" },
  { icon: <FiCode />, label: "Coding" },
  { icon: <FiDatabase />, label: "SQL" },
  { icon: <FiFileText />, label: "Resume tools" },
  { icon: <FiMic />, label: "AI interviews" },
  { icon: <FiTarget />, label: "Skill-gap analysis" },
  { icon: <FiBarChart2 />, label: "Performance tracking" },
  { icon: <FiSliders />, label: "Personalized preparation" }
];

const differentiators = [
  {
    icon: <FiUser />,
    title: "Personalized",
    text: "Preparation adapts to the user's target role, skill level and progress."
  },
  {
    icon: <FiFlag />,
    title: "Career-Focused",
    text: "Content is organised around real career paths and preparation goals."
  },
  {
    icon: <FiTrendingUp />,
    title: "Performance-Driven",
    text: "Practice, assessments and interviews contribute to readiness insights."
  },
  {
    icon: <FiRefreshCw />,
    title: "Continuous Improvement",
    text: "Results are used to recommend what the user should practise next."
  }
];

const flow = [
  { icon: <FiUser />, label: "Profile" },
  { icon: <FiFlag />, label: "Career Goal" },
  { icon: <FiTarget />, label: "Skill Gap" },
  { icon: <FiPlay />, label: "Practice" },
  { icon: <FiMic />, label: "Interview" },
  { icon: <FiBarChart2 />, label: "Performance" },
  { icon: <FiRefreshCw />, label: "Updated Recommendations" }
];

const responsibleAi = [
  "AI is used to assist preparation, not to replace your own judgement.",
  "Feedback is intended for learning and practice.",
  "AI should never invent qualifications, experience or achievements.",
  "Code and SQL execution will run in controlled, isolated environments.",
  "Users remain responsible for reviewing any generated suggestions."
];


function About() {
  return (
    <div className="about-page">

      {/* HERO */}

      <section className="about-hero">

        <div className="section-container about-hero-inner">

          <span className="section-tag">
            ABOUT CAREERAI
          </span>

          <h1>
            Career preparation, connected in
            <span> one intelligent platform</span>
          </h1>

          <p>
            CareerAI brings career planning, technical preparation, resume
            tools, mock interviews and performance feedback into one
            connected preparation experience.
          </p>

          <div className="about-actions">

            <Link
              to="/features"
              className="primary-button"
            >
              Explore CareerAI
              <FiArrowRight aria-hidden="true" />
            </Link>

            <Link
              to="/register"
              className="secondary-button"
            >
              Start Preparing
            </Link>

          </div>

        </div>

      </section>


      {/* WHAT CAREERAI IS */}

      <section className="home-section">

        <div className="section-container about-what">

          <div className="about-what-text">

            <span className="section-tag">
              WHAT CAREERAI IS
            </span>

            <h2>One platform for the whole preparation journey</h2>

            <p>
              CareerAI is a career-preparation platform. Instead of switching
              between separate tools, you plan, practise, prepare your resume,
              rehearse interviews and review your progress in one place.
            </p>

          </div>

          <ul className="about-capability-grid">
            {capabilities.map((item) => (
              <li key={item.label}>
                <span aria-hidden="true">{item.icon}</span>
                {item.label}
              </li>
            ))}
          </ul>

        </div>

      </section>


      {/* WHY CAREERAI WAS CREATED */}

      <section className="home-section about-why-section">

        <div className="section-container">

          <div className="section-heading centered">
            <span className="section-tag">
              WHY CAREERAI WAS CREATED
            </span>
            <h2>From scattered tools to a connected plan</h2>
          </div>

          <div className="about-why-grid">

            <div className="about-why-card problem">
              <span className="about-why-label">
                <FiShuffle aria-hidden="true" />
                The problem
              </span>
              <p>
                Career preparation is often fragmented across different
                websites, question banks, resume tools and interview platforms.
                Progress in one place rarely informs what to do in another.
              </p>
            </div>

            <div className="about-why-card goal">
              <span className="about-why-label">
                <FiLink aria-hidden="true" />
                CareerAI's goal
              </span>
              <p>
                CareerAI aims to connect these preparation activities so
                progress in one area can influence what the user should work
                on next.
              </p>
            </div>

          </div>

        </div>

      </section>


      {/* WHAT MAKES IT DIFFERENT */}

      <section className="home-section">

        <div className="section-container">

          <div className="section-heading centered">
            <span className="section-tag">
              WHAT MAKES IT DIFFERENT
            </span>
            <h2>Built around your goal, not a generic question bank</h2>
          </div>

          <div className="about-diff-grid">
            {differentiators.map((item) => (
              <div
                key={item.title}
                className="about-diff-card"
              >
                <span
                  className="about-diff-icon"
                  aria-hidden="true"
                >
                  {item.icon}
                </span>
                <h3>{item.title}</h3>
                <p>{item.text}</p>
              </div>
            ))}
          </div>

        </div>

      </section>


      {/* HOW PERSONALIZATION WORKS */}

      <section className="home-section about-flow-section">

        <div className="section-container about-flow-layout">

          <div className="about-flow-text">

            <span className="section-tag">
              HOW PERSONALIZATION WORKS
            </span>

            <h2>Every step feeds the next recommendation</h2>

            <p>
              Your profile and career goal define what matters. Skill-gap
              analysis, practice, interviews and performance results then
              keep updating what CareerAI recommends next.
            </p>

            <Link
              to="/how-it-works"
              className="primary-button"
            >
              See How CareerAI Works
              <FiArrowRight aria-hidden="true" />
            </Link>

          </div>

          <ol className="about-flow">
            {flow.map((step, index) => (
              <li key={step.label}>
                <div className="about-flow-step">
                  <span
                    className="about-flow-icon"
                    aria-hidden="true"
                  >
                    {step.icon}
                  </span>
                  <span className="about-flow-number">
                    {String(index + 1).padStart(2, "0")}
                  </span>
                  <strong>{step.label}</strong>
                </div>
                {index < flow.length - 1 && (
                  <FiArrowDown
                    className="about-flow-arrow"
                    aria-hidden="true"
                  />
                )}
              </li>
            ))}
          </ol>

        </div>

      </section>


      {/* RESPONSIBLE AI */}

      <section className="home-section">

        <div className="section-container">

          <div className="about-responsible">

            <div className="about-responsible-head">
              <span
                className="about-responsible-icon"
                aria-hidden="true"
              >
                <FiShield />
              </span>
              <div>
                <span className="section-tag light">
                  RESPONSIBLE AI
                </span>
                <h2>Responsible use of AI</h2>
                <p>
                  AI helps CareerAI give faster, more specific feedback. It is
                  applied with clear limits.
                </p>
              </div>
            </div>

            <ul className="about-responsible-list">
              {responsibleAi.map((item) => (
                <li key={item}>
                  <FiCheckCircle aria-hidden="true" />
                  {item}
                </li>
              ))}
            </ul>

          </div>

        </div>

      </section>


      {/* FOUNDER */}

      <section className="home-section about-founder-section">

        <div className="section-container about-founder">

          <div className="about-founder-card">

            <div
              className="about-founder-avatar"
              aria-hidden="true"
            >
              MK
            </div>

            <strong>Mugdha Manoj Kamble</strong>
            <span>Founder &amp; Developer, CareerAI</span>

            <div className="about-founder-connect">
              <div
                className="about-founder-connect-icons"
                aria-hidden="true"
              >
                <FiLinkedin />
                <FiGithub />
                <FiMail />
              </div>
              <small>Connect details can be added later.</small>
            </div>

          </div>

          <div className="about-founder-text">

            <span className="section-tag">
              FOUNDER
            </span>

            <h2>Mugdha Manoj Kamble</h2>

            <p className="about-founder-role">
              Founder &amp; Developer, CareerAI
            </p>

            <p>
              CareerAI was created as a full-stack career preparation platform
              designed to bring career planning, skill development, resume
              preparation, mock interviews and performance tracking into one
              connected system.
            </p>

            <p>
              The project focuses on combining modern web development,
              artificial intelligence and role-based preparation workflows to
              make career preparation more structured and practical.
            </p>

          </div>

        </div>

      </section>


      {/* FINAL CTA */}

      <section className="info-page-cta-section">

        <div className="section-container">

          <div className="info-page-cta">

            <div>
              <span className="section-tag light">
                GET STARTED
              </span>
              <h2>Build your preparation journey with CareerAI</h2>
            </div>

            <div className="info-page-cta-actions">

              <Link
                to="/career-tracks"
                className="cta-white-button"
              >
                Explore Career Tracks
                <FiArrowRight aria-hidden="true" />
              </Link>

              <Link
                to="/practice"
                className="info-page-cta-outline"
              >
                Start Practising
              </Link>

            </div>

          </div>

        </div>

      </section>

    </div>
  );
}

export default About;
