import { Link } from "react-router-dom";

import {
  FiMic,
  FiFileText,
  FiCode,
  FiDatabase,
  FiTarget,
  FiBarChart2,
  FiBookOpen,
  FiBriefcase,
  FiCpu,
  FiMessageCircle,
  FiTrendingUp,
  FiCheckCircle,
  FiArrowRight,
  FiAward,
  FiLayers,
  FiUsers
} from "react-icons/fi";


function Features() {

  const mainFeatures = [

    {
      icon: <FiMic />,
      title: "AI Mock Interviews",
      description:
        "Practice technical, HR, behavioural and mixed interviews with an adaptive AI interviewer designed to ask relevant follow-up questions.",
      points: [
        "Role-based interviews",
        "Resume-based interviews",
        "Job-description-based interviews",
        "Interview feedback and reports"
      ],
      path: "/ai-interview",
      action: "Explore AI Interviews"
    },

    {
      icon: <FiFileText />,
      title: "Resume Tools",
      description:
        "Create professional resumes, analyse resume readiness and compare your resume with a target job description.",
      points: [
        "Resume Builder",
        "ATS Readiness Analysis",
        "Resume and Job Match",
        "Resume Versions"
      ],
      path: "/resume-tools",
      action: "Open Resume Tools"
    },

    {
      icon: <FiTarget />,
      title: "Career Planning",
      description:
        "Understand your target role, required skills and preparation priorities through structured career tools.",
      points: [
        "Career Assessment",
        "Career Roadmap",
        "Skill Gap Analysis",
        "Job Readiness"
      ],
      path: "/career-tracks",
      action: "Explore Career Paths"
    },

    {
      icon: <FiBookOpen />,
      title: "Technical Preparation",
      description:
        "Prepare interview topics across multiple programming languages, frameworks, databases and computer science subjects.",
      points: [
        "Java, Python and JavaScript",
        "React and Spring Boot",
        "SQL and DBMS",
        "Operating Systems and Networks"
      ],
      path: "/practice/technical",
      action: "Start Technical Practice"
    },

    {
      icon: <FiCode />,
      title: "Coding Practice",
      description:
        "Solve coding problems using multiple programming languages and review your submissions and results.",
      points: [
        "Multiple programming languages",
        "Problem difficulty levels",
        "Test case evaluation",
        "Submission history"
      ],
      path: "/practice/coding",
      action: "Start Coding"
    },

    {
      icon: <FiDatabase />,
      title: "SQL Practice",
      description:
        "Practice SQL queries using controlled datasets instead of working against the real CareerAI database.",
      points: [
        "Practice datasets",
        "SQL query challenges",
        "Result evaluation",
        "Submission tracking"
      ],
      path: "/practice/sql",
      action: "Start SQL Practice"
    },

    {
      icon: <FiCpu />,
      title: "Aptitude & Assessments",
      description:
        "Prepare for recruitment assessments with aptitude questions, mock tests and timed practice.",
      points: [
        "Quantitative aptitude",
        "Logical reasoning",
        "Verbal ability",
        "Data interpretation"
      ],
      path: "/practice/aptitude",
      action: "Start Aptitude Practice"
    },

    {
      icon: <FiBarChart2 />,
      title: "Performance Analytics",
      description:
        "Track your preparation over time and understand which areas are improving and which still need attention.",
      points: [
        "Interview trends",
        "Skill performance",
        "Practice performance",
        "Progress comparison"
      ],
      path: "/performance",
      action: "View Performance"
    },

    {
      icon: <FiMessageCircle />,
      title: "AI Career Coach",
      description:
        "Receive preparation guidance based on your target role, profile, weak areas, interview history and current progress.",
      points: [
        "Personalized suggestions",
        "Daily preparation guidance",
        "Weak-area recommendations",
        "Career preparation support"
      ],
      path: "/coach",
      action: "Open AI Coach"
    }

  ];


  const extraFeatures = [

    {
      icon: <FiTrendingUp />,
      title: "Personalized Preparation Plan",
      description:
        "CareerAI can organize preparation tasks based on your goals and performance.",
      path: "/my-plan"
    },

    {
      icon: <FiBriefcase />,
      title: "Job Application Tracker",
      description:
        "Track saved jobs, applications, assessments, interviews and offers.",
      path: "/applications"
    },

    {
      icon: <FiAward />,
      title: "Achievements",
      description:
        "Track milestones such as completed interviews, streaks and practice goals.",
      path: "/achievements"
    },

    {
      icon: <FiLayers />,
      title: "Question Engine",
      description:
        "Questions can come from CareerAI content, external providers and AI generation.",
      path: "/question-bank"
    },

    {
      icon: <FiUsers />,
      title: "Career Tracks",
      description:
        "Follow structured role-based paths for different career goals.",
      path: "/career-tracks"
    },

    {
      icon: <FiCheckCircle />,
      title: "Daily Challenge",
      description:
        "Complete daily technical, aptitude or coding challenges and maintain progress.",
      path: "/practice/daily-challenge"
    }

  ];


  return (
    <div className="features-page">

      {/* ==================================================
          HERO
      ================================================== */}

      <section className="inner-page-hero">

        <div className="section-container inner-page-hero-content">

          <span className="section-tag">
            CAREERAI FEATURES
          </span>


          <h1>
            Everything you need to prepare for your career
          </h1>


          <p>
            CareerAI combines career planning, interview preparation,
            resume tools, technical practice, assessments and performance
            tracking into one connected platform.
          </p>


          <div className="inner-page-actions">

            <Link
              to="/register"
              className="primary-button"
            >
              Get Started
              <FiArrowRight />
            </Link>


            <Link
              to="/how-it-works"
              className="secondary-button"
            >
              See How It Works
            </Link>

          </div>

        </div>

      </section>


      {/* ==================================================
          MAIN FEATURES
      ================================================== */}

      <section className="home-section">

        <div className="section-container">

          <div className="section-heading centered">

            <span className="section-tag">
              COMPLETE PREPARATION PLATFORM
            </span>


            <h2>
              More than just interview practice
            </h2>


            <p>
              CareerAI is designed to support the complete preparation
              journey, from choosing a career direction to improving
              after an interview.
            </p>

          </div>


          <div className="features-detail-grid">

            {mainFeatures.map((feature) => (

              <Link
                to={feature.path}
                className="feature-detail-card feature-detail-link"
                key={feature.title}
              >

                <div className="feature-detail-icon">
                  {feature.icon}
                </div>


                <h3>
                  {feature.title}
                </h3>


                <p>
                  {feature.description}
                </p>


                <ul>

                  {feature.points.map((point) => (

                    <li key={point}>

                      <FiCheckCircle />

                      {point}

                    </li>

                  ))}

                </ul>


                <span className="feature-open-link">

                  {feature.action}

                  <FiArrowRight />

                </span>

              </Link>

            ))}

          </div>

        </div>

      </section>


      {/* ==================================================
          PERSONALIZATION
      ================================================== */}

      <section className="home-section feature-personalization-section">

        <div className="section-container split-section">

          <div className="split-content">

            <span className="section-tag">
              PERSONALIZED PREPARATION
            </span>


            <h2>
              CareerAI learns from your preparation activity
            </h2>


            <p>
              Different users should not receive the same generic
              recommendations. CareerAI uses profile information,
              practice performance, interview results and skill scores
              to identify what each user should work on next.
            </p>


            <ul className="check-list">

              <li>
                <FiCheckCircle />
                Identify stronger and weaker skills
              </li>

              <li>
                <FiCheckCircle />
                Recommend relevant practice topics
              </li>

              <li>
                <FiCheckCircle />
                Update preparation priorities
              </li>

              <li>
                <FiCheckCircle />
                Track improvement over time
              </li>

            </ul>


            <Link
              to="/my-plan"
              className="text-link"
            >
              View Personalized Plan
              <FiArrowRight />
            </Link>

          </div>


          <div className="personalization-card">

            <div className="personalization-header">

              <span>
                YOUR CURRENT FOCUS
              </span>

              <strong>
                Java Full Stack Developer
              </strong>

            </div>


            <div className="personalization-item strong">

              <div>
                <span>Java</span>
                <strong>Strong</strong>
              </div>

              <div className="personalization-progress">
                <div style={{ width: "88%" }}></div>
              </div>

            </div>


            <div className="personalization-item strong">

              <div>
                <span>SQL</span>
                <strong>Strong</strong>
              </div>

              <div className="personalization-progress">
                <div style={{ width: "84%" }}></div>
              </div>

            </div>


            <div className="personalization-item">

              <div>
                <span>Spring Boot</span>
                <strong>Improving</strong>
              </div>

              <div className="personalization-progress">
                <div style={{ width: "72%" }}></div>
              </div>

            </div>


            <div className="personalization-item weak">

              <div>
                <span>
                  Spring Security
                </span>

                <strong>
                  Needs Practice
                </strong>
              </div>

              <div className="personalization-progress">
                <div style={{ width: "54%" }}></div>
              </div>

            </div>


            <Link
              to="/practice/technical"
              className="personalization-recommendation personalization-recommendation-link"
            >

              <FiTarget />

              <div>

                <span>
                  Recommended next
                </span>

                <strong>
                  Practice Spring Security
                </strong>

              </div>

              <FiArrowRight />

            </Link>

          </div>

        </div>

      </section>


      {/* ==================================================
          MORE FEATURES
      ================================================== */}

      <section className="home-section">

        <div className="section-container">

          <div className="section-heading centered">

            <span className="section-tag">
              MORE TO EXPLORE
            </span>


            <h2>
              Tools that keep your preparation connected
            </h2>


            <p>
              CareerAI connects planning, practice, progress and career
              tools so your preparation does not stop at one activity.
            </p>

          </div>


          <div className="extra-feature-grid">

            {extraFeatures.map((feature) => (

              <Link
                to={feature.path}
                className="extra-feature-card extra-feature-link"
                key={feature.title}
              >

                <div className="extra-feature-icon">
                  {feature.icon}
                </div>


                <div className="extra-feature-content">

                  <h3>
                    {feature.title}
                  </h3>

                  <p>
                    {feature.description}
                  </p>


                  <span className="extra-feature-action">

                    Open

                    <FiArrowRight />

                  </span>

                </div>

              </Link>

            ))}

          </div>

        </div>

      </section>


      {/* ==================================================
          CTA
      ================================================== */}

      <section className="feature-bottom-cta">

        <div className="section-container">

          <div className="feature-cta-box">

            <div>

              <span className="section-tag light">
                START PREPARING
              </span>


              <h2>
                Build a preparation plan around your career goal.
              </h2>


              <p>
                Create your profile, choose your target role and start
                improving one skill at a time.
              </p>

            </div>


            <Link
              to="/register"
              className="cta-white-button"
            >
              Create Account
              <FiArrowRight />
            </Link>

          </div>

        </div>

      </section>

    </div>
  );
}


export default Features;