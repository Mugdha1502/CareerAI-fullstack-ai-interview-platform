import { Link } from "react-router-dom";

import {
  FiArrowRight,
  FiMessageSquare,
  FiFileText,
  FiCode,
  FiCompass,
  FiBookOpen,
  FiLayers,
  FiDatabase,
  FiCheckSquare,
  FiUsers,
  FiTarget,
  FiPlay,
  FiSearch,
  FiMap,
  FiMic,
  FiTrendingUp,
  FiRepeat,
  FiClipboard,
  FiGitMerge
} from "react-icons/fi";

const categories = [
  {
    id: "interview",
    icon: <FiMessageSquare />,
    title: "Interview Guides",
    description: "Prepare for technical, HR and behavioural interviews with structured guidance.",
    links: [
      { label: "Technical Interview Guide", to: "/practice/technical" },
      { label: "HR Interview Guide", to: "/practice/voice" },
      { label: "Behavioural Interview Guide", to: "/practice/voice" },
      { label: "STAR Method Guide", to: "/practice/voice" }
    ]
  },
  {
    id: "resume",
    icon: <FiFileText />,
    title: "Resume Resources",
    description: "Improve resume quality, ATS readiness and job-description alignment.",
    links: [
      { label: "ATS Resume Checklist", to: "/resume/ats-analyzer" },
      { label: "Resume Writing Tips", to: "/resume/ai-improvement" },
      { label: "Job Match Tips", to: "/resume/job-match" }
    ]
  },
  {
    id: "technical",
    icon: <FiCode />,
    title: "Technical Resources",
    description: "Jump directly into important technologies and interview topics.",
    links: [
      { label: "Java", to: "/practice/technical?topic=Java" },
      { label: "SQL", to: "/practice/sql" },
      { label: "Spring Boot", to: "/practice/technical?topic=Spring%20Boot" },
      { label: "React", to: "/practice/technical?topic=React" },
      { label: "System Design", to: "/practice/system-design" }
    ]
  },
  {
    id: "career",
    icon: <FiCompass />,
    title: "Career Planning",
    description: "Understand target roles, skill gaps and preparation priorities.",
    links: [
      { label: "Explore Career Tracks", to: "/career-tracks" },
      { label: "Skill Gap Analysis", to: "/career/skill-gap" },
      { label: "My Preparation Plan", to: "/my-plan" }
    ]
  }
];

const featured = [
  {
    icon: <FiBookOpen />,
    type: "Technical Practice",
    title: "Java Interview Preparation",
    description: "OOP, collections, exceptions, multithreading and JVM questions with explanations.",
    to: "/practice/technical?topic=Java"
  },
  {
    icon: <FiLayers />,
    type: "Technical Practice",
    title: "Spring Boot Interview Preparation",
    description: "Dependency injection, auto-configuration and REST API questions for backend roles.",
    to: "/practice/technical?topic=Spring%20Boot"
  },
  {
    icon: <FiDatabase />,
    type: "SQL Workspace",
    title: "SQL Practice Guide",
    description: "Write queries against practice datasets — joins, grouping, subqueries and more.",
    to: "/practice/sql"
  },
  {
    icon: <FiCheckSquare />,
    type: "Resume Tool",
    title: "ATS Resume Checklist",
    description: "Check formatting, keywords and section structure before you apply.",
    to: "/resume/ats-analyzer"
  },
  {
    icon: <FiUsers />,
    type: "Voice Practice",
    title: "Behavioural Interview Preparation",
    description: "Practise structured STAR answers and get feedback on clarity and relevance.",
    to: "/practice/voice"
  },
  {
    icon: <FiTarget />,
    type: "Career Planning",
    title: "Career Readiness Guide",
    description: "See which skills for your target role are strong and which need attention.",
    to: "/career/skill-gap"
  }
];

const quickActions = [
  {
    icon: <FiPlay />,
    title: "Start Practice",
    description: "Technical, aptitude, coding, SQL and more.",
    to: "/practice"
  },
  {
    icon: <FiSearch />,
    title: "Analyze Resume",
    description: "Check ATS readiness and missing keywords.",
    to: "/resume/ats-analyzer"
  },
  {
    icon: <FiMap />,
    title: "Explore Career Tracks",
    description: "Role roadmaps, skills and projects.",
    to: "/career-tracks"
  },
  {
    icon: <FiMic />,
    title: "Start AI Interview",
    description: "Practise a realistic mock interview.",
    to: "/ai-interview"
  }
];

const tips = [
  {
    icon: <FiTrendingUp />,
    title: "Focus on weak areas first",
    text: "Use Skill Gap and practice results to spend time where it moves your readiness most."
  },
  {
    icon: <FiRepeat />,
    title: "Practice consistently",
    text: "Short daily sessions build retention better than occasional long ones."
  },
  {
    icon: <FiClipboard />,
    title: "Review feedback after each session",
    text: "Read explanations for every mistake before moving to the next set."
  },
  {
    icon: <FiGitMerge />,
    title: "Combine technical and interview preparation",
    text: "Knowing a concept and explaining it aloud are different skills — practise both."
  }
];

const previewItems = [
  { icon: <FiMessageSquare />, label: "Interview", detail: "Technical, HR & behavioural" },
  { icon: <FiFileText />, label: "Resume", detail: "ATS, writing & job match" },
  { icon: <FiCode />, label: "Technical", detail: "Java, SQL, Spring Boot, React" },
  { icon: <FiCompass />, label: "Career Planning", detail: "Tracks, skill gap & plan" }
];


function Resources() {
  return (
    <div className="resources-page">

      {/* HERO */}

      <section className="resources-hero">

        <div className="section-container resources-hero-grid">

          <div className="resources-hero-content">

            <span className="section-tag">
              CAREER RESOURCES
            </span>

            <h1>
              Everything you need to
              <span> prepare smarter</span>
            </h1>

            <p>
              Explore practical guides, technical preparation resources,
              resume tools and career-planning support in one place.
            </p>

            <div className="resources-hero-actions">

              <a
                href="#resource-categories"
                className="primary-button"
              >
                Explore Resources
                <FiArrowRight />
              </a>

              <Link
                to="/practice"
                className="secondary-button"
              >
                <FiPlay />
                Start Practising
              </Link>

            </div>

          </div>


          <div
            className="resources-hero-preview"
            aria-hidden="true"
          >

            <div className="resources-preview-header">
              <span>RESOURCE LIBRARY</span>
              <strong>4 preparation areas</strong>
            </div>

            <div className="resources-preview-grid">
              {previewItems.map((item) => (
                <div
                  key={item.label}
                  className="resources-preview-item"
                >
                  <span className="resources-preview-icon">
                    {item.icon}
                  </span>
                  <strong>{item.label}</strong>
                  <small>{item.detail}</small>
                </div>
              ))}
            </div>

          </div>

        </div>

      </section>


      {/* CATEGORIES */}

      <section
        className="home-section resources-categories-section"
        id="resource-categories"
      >

        <div className="section-container">

          <div className="section-heading centered">

            <span className="section-tag">
              RESOURCE CATEGORIES
            </span>

            <h2>
              Find the right resource for your next step
            </h2>

            <p>
              Each resource opens a working CareerAI tool or practice module.
            </p>

          </div>


          <div className="resources-category-grid">

            {categories.map((category) => (
              <article
                key={category.id}
                className={`resources-category-card ${category.id}`}
              >

                <div className="resources-category-head">

                  <span className="resources-category-icon">
                    {category.icon}
                  </span>

                  <div>
                    <h3>{category.title}</h3>
                    <p>{category.description}</p>
                  </div>

                </div>

                <ul className="resources-link-list">
                  {category.links.map((link) => (
                    <li key={link.label}>
                      <Link to={link.to}>
                        <span>{link.label}</span>
                        <FiArrowRight />
                      </Link>
                    </li>
                  ))}
                </ul>

              </article>
            ))}

          </div>

        </div>

      </section>


      {/* FEATURED */}

      <section className="home-section resources-featured-section">

        <div className="section-container">

          <div className="section-heading centered">

            <span className="section-tag">
              FEATURED RESOURCES
            </span>

            <h2>
              Popular preparation paths
            </h2>

          </div>


          <div className="resources-featured-grid">

            {featured.map((item) => (
              <Link
                key={item.title}
                to={item.to}
                className="resources-featured-card"
              >

                <div className="resources-featured-top">
                  <span className="resources-featured-icon">
                    {item.icon}
                  </span>
                  <span className="resources-featured-type">
                    {item.type}
                  </span>
                </div>

                <h3>{item.title}</h3>

                <p>{item.description}</p>

                <span className="resources-featured-open">
                  Open Resource
                  <FiArrowRight />
                </span>

              </Link>
            ))}

          </div>

        </div>

      </section>


      {/* QUICK ACTIONS */}

      <section className="resources-quick-section">

        <div className="section-container">

          <div className="resources-quick-panel">

            <div className="resources-quick-heading">
              <span className="section-tag light">
                QUICK ACTIONS
              </span>
              <h2>Turn learning into practice</h2>
              <p>Pick one action and start a focused session right now.</p>
            </div>

            <div className="resources-quick-grid">

              {quickActions.map((action) => (
                <Link
                  key={action.title}
                  to={action.to}
                  className="resources-quick-card"
                >
                  <span className="resources-quick-icon">
                    {action.icon}
                  </span>

                  <strong>{action.title}</strong>

                  <small>{action.description}</small>

                  <FiArrowRight className="resources-quick-arrow" />
                </Link>
              ))}

            </div>

          </div>

        </div>

      </section>


      {/* TIPS */}

      <section className="home-section resources-tips-section">

        <div className="section-container">

          <div className="section-heading centered">

            <span className="section-tag">
              STUDY TIPS
            </span>

            <h2>
              Make the most of CareerAI
            </h2>

          </div>


          <ul className="resources-tips-grid">

            {tips.map((tip) => (
              <li
                key={tip.title}
                className="resources-tip"
              >
                <span className="resources-tip-icon">
                  {tip.icon}
                </span>
                <div>
                  <strong>{tip.title}</strong>
                  <p>{tip.text}</p>
                </div>
              </li>
            ))}

          </ul>

        </div>

      </section>


      {/* FINAL CTA */}

      <section className="resources-cta-section">

        <div className="section-container">

          <div className="resources-cta">

            <div>

              <span className="section-tag light">
                START YOUR ROUTINE
              </span>

              <h2>
                Build a preparation routine that fits your goal
              </h2>

              <p>
                Choose your target role, practise the right skills and track
                your progress as you prepare.
              </p>

            </div>

            <div className="resources-cta-actions">

              <Link
                to="/career-tracks"
                className="cta-white-button"
              >
                Explore Career Tracks
                <FiArrowRight />
              </Link>

              <Link
                to="/practice"
                className="resources-cta-outline"
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

export default Resources;
