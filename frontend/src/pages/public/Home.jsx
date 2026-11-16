import { Link } from "react-router-dom";

import {
  FiArrowRight,
  FiCheck,
  FiFileText,
  FiMic,
  FiCode,
  FiBarChart2,
  FiTarget,
  FiBookOpen,
  FiTrendingUp,
  FiUsers,
  FiBriefcase,
  FiAward,
  FiPlay,
  FiUser,
  FiSearch,
  FiCalendar,
  FiMessageSquare
} from "react-icons/fi";


function Home() {

  const features = [
    {
      icon: <FiMic />,
      title: "AI Mock Interviews",
      description:
        "Practise realistic technical, HR and behavioural interviews with an adaptive AI interviewer.",
      path: "/ai-interview"
    },

    {
      icon: <FiFileText />,
      title: "Resume Intelligence",
      description:
        "Create ATS-friendly resumes, analyse resume quality and compare your resume with job descriptions.",
      path: "/resume-tools"
    },

    {
      icon: <FiCode />,
      title: "Technical Practice",
      description:
        "Prepare Java, Python, JavaScript, SQL, Spring Boot, React and other important technologies.",
      path: "/practice/technical"
    },

    {
      icon: <FiTarget />,
      title: "Skill Gap Analysis",
      description:
        "Understand your stronger and weaker skills based on your career goal and preparation performance.",
      path: "/career/skill-gap"
    },

    {
      icon: <FiBarChart2 />,
      title: "Performance Analytics",
      description:
        "Track interview scores, practice performance, skill improvement and preparation progress.",
      path: "/performance"
    },

    {
      icon: <FiBookOpen />,
      title: "Personalized Preparation",
      description:
        "Get preparation recommendations based on your target role, skills and previous performance.",
      path: "/my-plan"
    }
  ];


  const careerTracks = [
    {
      title: "Java Full Stack Developer",
      path: "/career-tracks/java-full-stack"
    },

    {
      title: "Frontend Developer",
      path: "/career-tracks/frontend-developer"
    },

    {
      title: "Backend Developer",
      path: "/career-tracks/backend-developer"
    },

    {
      title: "Python Developer",
      path: "/career-tracks/python-developer"
    },

    {
      title: "Data Analyst",
      path: "/career-tracks/data-analyst"
    },

    {
      title: "Software Engineer",
      path: "/career-tracks/software-engineer"
    }
  ];


  const journey = [
    {
      number: 1,
      icon: <FiUser />,
      title: "Create your profile",
      description:
        "Tell CareerAI about your skills, education and current career stage.",
      path: "/register",
      action: "Create profile"
    },

    {
      number: 2,
      icon: <FiTarget />,
      title: "Choose your career goal",
      description:
        "Select the role you want to prepare for and explore its requirements.",
      path: "/career-tracks",
      action: "Explore careers"
    },

    {
      number: 3,
      icon: <FiSearch />,
      title: "Assess your skills",
      description:
        "Compare your current strengths with the skills your target role requires.",
      path: "/career/skill-gap",
      action: "View skill gap"
    },

    {
      number: 4,
      icon: <FiCalendar />,
      title: "Follow your preparation plan",
      description:
        "Work through personalized tasks based on your goal and available time.",
      path: "/my-plan",
      action: "View preparation plan"
    },

    {
      number: 5,
      icon: <FiBookOpen />,
      title: "Practice and improve",
      description:
        "Strengthen technical, aptitude, coding and SQL skills through practice.",
      path: "/practice",
      action: "Start practising"
    },

    {
      number: 6,
      icon: <FiMic />,
      title: "Take an AI interview",
      description:
        "Practise technical, HR and behavioural interviews in a realistic environment.",
      path: "/ai-interview",
      action: "Explore interviews"
    },

    {
      number: 7,
      icon: <FiMessageSquare />,
      title: "Review your feedback",
      description:
        "Use performance insights to identify what you should improve next.",
      path: "/performance",
      action: "View performance"
    }
  ];


  return (
    <div className="home-page">

      {/* ==================================================
          HERO
      ================================================== */}

      <section className="hero-section">

        <div className="hero-background hero-background-one"></div>
        <div className="hero-background hero-background-two"></div>

        <div className="section-container hero-container">

          <div className="hero-content">

            <div className="hero-badge">
              <span>
                AI-powered career preparation
              </span>
            </div>


            <h1>
              Prepare smarter.
              <br />
              Interview{" "}
              <span>
                with confidence.
              </span>
            </h1>


            <p className="hero-description">
              CareerAI brings your career preparation into one place —
              personalized learning, resume tools, technical practice,
              AI interviews and performance insights built around your
              career goals.
            </p>


            <div className="hero-actions">

              <Link
                to="/register"
                className="primary-button"
              >
                Start Preparing
                <FiArrowRight />
              </Link>


              <Link
                to="/ai-interview"
                className="secondary-button"
              >
                <FiPlay />
                Explore AI Interview
              </Link>

            </div>


            <div className="hero-points">

              <span>
                <FiCheck />
                Personalized preparation
              </span>

              <span>
                <FiCheck />
                Real interview practice
              </span>

              <span>
                <FiCheck />
                Progress tracking
              </span>

            </div>

          </div>


          {/* DASHBOARD PREVIEW */}

          <div className="hero-dashboard">

            <div className="dashboard-preview-header">

              <div>

                <span className="preview-label">
                  CAREER READINESS
                </span>

                <h3>
                  Your preparation overview
                </h3>

              </div>


              <div className="readiness-circle">
                78%
              </div>

            </div>


            <div className="preview-stats">

              <div>
                <span>
                  Interview Score
                </span>

                <strong>
                  82%
                </strong>
              </div>


              <div>
                <span>
                  Questions Practised
                </span>

                <strong>
                  146
                </strong>
              </div>


              <div>
                <span>
                  Current Streak
                </span>

                <strong>
                  7 days
                </strong>
              </div>

            </div>


            <div className="recommendation-card">

              <div className="recommendation-icon">
                <FiTrendingUp />
              </div>


              <div>

                <span>
                  Recommended next
                </span>

                <strong>
                  Spring Security Practice
                </strong>

                <p>
                  Improve your authentication and authorization concepts.
                </p>

              </div>

            </div>


            <div className="skill-preview">

              <SkillPreview
                name="Java"
                value={88}
              />

              <SkillPreview
                name="SQL"
                value={84}
              />

              <SkillPreview
                name="Spring Boot"
                value={72}
              />

              <SkillPreview
                name="Spring Security"
                value={54}
                weak
              />

            </div>


            <Link
              to="/dashboard"
              className="dashboard-preview-link"
            >
              View your dashboard
              <FiArrowRight />
            </Link>

          </div>

        </div>

      </section>


      {/* ==================================================
          BENEFITS / TRUST
      ================================================== */}

      <section className="stats-section">

        <div className="section-container stats-grid">

          <div>
            <FiUsers />

            <strong>
              Personalized
            </strong>

            <span>
              For every learner
            </span>
          </div>


          <div>
            <FiBriefcase />

            <strong>
              Career-focused
            </strong>

            <span>
              Role-based preparation
            </span>
          </div>


          <div>
            <FiAward />

            <strong>
              Performance-driven
            </strong>

            <span>
              Learn from your results
            </span>
          </div>


          <div>
            <FiTrendingUp />

            <strong>
              Continuous improvement
            </strong>

            <span>
              Track progress over time
            </span>
          </div>

        </div>

      </section>


      {/* ==================================================
          FEATURES
      ================================================== */}

      <section className="home-section">

        <div className="section-container">

          <div className="section-heading centered">

            <span className="section-tag">
              EVERYTHING YOU NEED
            </span>


            <h2>
              One platform for your complete career preparation
            </h2>


            <p>
              Prepare for each stage of the recruitment process without
              switching between multiple tools.
            </p>

          </div>


          <div className="feature-grid">

            {features.map((feature) => (

              <Link
                to={feature.path}
                className="feature-card home-clickable-card"
                key={feature.title}
              >

                <div className="feature-icon">
                  {feature.icon}
                </div>


                <h3>
                  {feature.title}
                </h3>


                <p>
                  {feature.description}
                </p>


                <span className="home-card-action">
                  Explore feature
                  <FiArrowRight />
                </span>

              </Link>

            ))}

          </div>


          <div className="home-section-action">

            <Link
              to="/features"
              className="text-link"
            >
              View all CareerAI features
              <FiArrowRight />
            </Link>

          </div>

        </div>

      </section>


      {/* ==================================================
          AI INTERVIEW
      ================================================== */}

      <section className="home-section alternate-section">

        <div className="section-container split-section">

          <div className="split-content">

            <span className="section-tag">
              AI INTERVIEW
            </span>


            <h2>
              Practise interviews that respond to your answers
            </h2>


            <p>
              CareerAI is designed to provide conversational interview
              practice rather than simply displaying a fixed question list.
            </p>


            <ul className="check-list">

              <li>
                <FiCheck />
                Technical, HR, behavioural and mixed interviews
              </li>

              <li>
                <FiCheck />
                Role-based and resume-based interview preparation
              </li>

              <li>
                <FiCheck />
                Adaptive follow-up questions
              </li>

              <li>
                <FiCheck />
                Detailed performance feedback
              </li>

            </ul>


            <Link
              to="/ai-interview"
              className="text-link"
            >
              Explore AI Interviews
              <FiArrowRight />
            </Link>

          </div>


          <div className="interview-preview">

            <div className="interview-topbar">

              <div>
                <span className="live-dot"></span>
                AI Interview
              </div>

              <span>
                12:45
              </span>

            </div>


            <div className="interviewer-area">

              <div className="avatar-placeholder">
                AI
              </div>


              <h3>
                CareerAI Interviewer
              </h3>


              <span className="speaking-state">
                Listening...
              </span>

            </div>


            <div className="interview-question">

              <span>
                Current question
              </span>

              <p>
                How did you handle authentication in your Spring Boot
                application?
              </p>

            </div>


            <div className="interview-controls">

              <Link
                to="/ai-interview"
                className="home-interview-control"
                aria-label="Open AI interview"
              >
                <FiMic />
              </Link>


              <Link
                to="/ai-interview"
                className="home-interview-control"
                aria-label="Start interview demo"
              >
                <FiPlay />
              </Link>

            </div>

          </div>

        </div>

      </section>


      {/* ==================================================
          CAREER TRACKS
      ================================================== */}

      <section className="home-section">

        <div className="section-container">

          <div className="section-heading">

            <div>

              <span className="section-tag">
                CAREER TRACKS
              </span>


              <h2>
                Prepare for the role you actually want
              </h2>

            </div>


            <Link
              to="/career-tracks"
              className="text-link"
            >
              View all tracks
              <FiArrowRight />
            </Link>

          </div>


          <div className="career-track-grid">

            {careerTracks.map(
              (track, index) => (

                <Link
                  to={track.path}
                  className="career-track-card home-clickable-card"
                  key={track.title}
                >

                  <div className="track-number">
                    {String(index + 1).padStart(2, "0")}
                  </div>


                  <h3>
                    {track.title}
                  </h3>


                  <p>
                    Structured skills, preparation topics and interview
                    practice for this career path.
                  </p>


                  <span className="home-card-action">
                    Explore track
                    <FiArrowRight />
                  </span>

                </Link>

              )
            )}

          </div>

        </div>

      </section>


      {/* ==================================================
          HOW CAREERAI WORKS
      ================================================== */}

      <section className="home-section journey-section">

        <div className="section-container">

          <div className="section-heading centered">

            <span className="section-tag">
              HOW CAREERAI WORKS
            </span>


            <h2>
              From career goal to interview readiness
            </h2>


            <p>
              CareerAI connects your preparation activity so each step can
              help shape what you practise next.
            </p>

          </div>


          <div className="home-journey-grid">

            {journey.map((item) => (

              <Link
                to={item.path}
                className="home-journey-card"
                key={item.number}
              >

                <div className="home-journey-top">

                  <div className="home-journey-number">
                    {String(item.number).padStart(2, "0")}
                  </div>


                  <div className="home-journey-icon">
                    {item.icon}
                  </div>

                </div>


                <h3>
                  {item.title}
                </h3>


                <p>
                  {item.description}
                </p>


                <span>
                  {item.action}
                  <FiArrowRight />
                </span>

              </Link>

            ))}

          </div>


          <div className="home-section-action">

            <Link
              to="/how-it-works"
              className="secondary-button"
            >
              See How CareerAI Works
              <FiArrowRight />
            </Link>

          </div>

        </div>

      </section>


      {/* ==================================================
          CTA
      ================================================== */}

      <section className="home-cta-section">

        <div className="section-container">

          <div className="home-cta">

            <div>

              <span className="section-tag light">
                START YOUR PREPARATION
              </span>


              <h2>
                Your next opportunity deserves better preparation.
              </h2>


              <p>
                Build your CareerAI profile, select your target role and
                begin a preparation journey designed around your goals.
              </p>

            </div>


            <div className="home-final-actions">

              <Link
                to="/register"
                className="cta-white-button"
              >
                Create Free Account
                <FiArrowRight />
              </Link>


              <Link
                to="/login"
                className="home-guest-link"
              >
                Or continue as guest
              </Link>

            </div>

          </div>

        </div>

      </section>

    </div>
  );
}


function SkillPreview({
  name,
  value,
  weak
}) {

  return (
    <div
      className={`skill-row ${
        weak ? "weak" : ""
      }`}
    >

      <div>

        <span>
          {name}
        </span>

        <strong>
          {value}%
        </strong>

      </div>


      <div className="skill-bar">

        <div
          style={{
            width: `${value}%`
          }}
        ></div>

      </div>

    </div>
  );
}


export default Home;