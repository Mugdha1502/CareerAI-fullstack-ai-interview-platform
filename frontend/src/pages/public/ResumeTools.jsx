import { useState } from "react";
import { Link } from "react-router-dom";

import {
  FiFileText,
  FiSearch,
  FiTarget,
  FiEdit3,
  FiLayers,
  FiCheckCircle,
  FiArrowRight,
  FiBriefcase,
  FiAlertCircle,
  FiTrendingUp,
  FiDownload,
  FiEye,
  FiPlus,
  FiZap
} from "react-icons/fi";

function ResumeTools() {
  const [activeTool, setActiveTool] = useState("ats");

  const tools = {
    ats: {
      title: "ATS Readiness Analysis",
      description:
        "Review resume structure, keyword coverage, readability, contact information and skill coverage.",
      score: "82%",
      label: "Example readiness"
    },

    match: {
      title: "Resume & Job Match",
      description:
        "Compare your resume with a job description to identify matched skills, missing skills and preparation priorities.",
      score: "76%",
      label: "Example match"
    },

    improve: {
      title: "Resume Wording Improvement",
      description:
        "Tidy wording and get suggestions based only on information you actually provide. AI improvement arrives in the AI integration phase.",
      score: "8",
      label: "Example suggestions"
    }
  };

  const resumeFeatures = [
    {
      icon: <FiEdit3 />,
      title: "Resume Builder",
      description:
        "Create a structured, professional resume using ATS-friendly sections and templates.",
      path: "/resume/builder"
    },
    {
      icon: <FiSearch />,
      title: "ATS Analyzer",
      description:
        "Review formatting, structure, keywords, readability and skill coverage.",
      path: "/resume/ats-analyzer"
    },
    {
      icon: <FiTarget />,
      title: "Job Match",
      description:
        "Compare your resume with a job description and identify gaps.",
      path: "/resume/job-match"
    },
    {
      icon: <FiZap />,
      title: "AI Improvement",
      description:
        "Tidy wording without inventing achievements or experience. AI rewriting is coming soon.",
      path: "/resume/ai-improvement"
    },
    {
      icon: <FiLayers />,
      title: "Resume Versions",
      description:
        "Keep named versions of each resume for different opportunities.",
      path: "/resume/versions"
    },
    {
      icon: <FiBriefcase />,
      title: "Interview Preparation",
      description:
        "Use resume and job information to find the topics to prepare.",
      path: "/resume/interview-preparation"
    }
  ];

  return (
    <div className="resume-tools-page">

      {/* ================= HERO ================= */}

      <section className="resume-tools-hero">

        <div className="section-container resume-hero-grid">

          <div className="resume-hero-content">

            <span className="section-tag">
              CAREERAI RESUME TOOLS
            </span>

            <h1>
              Build a resume that supports your
              <span> next opportunity.</span>
            </h1>

            <p>
              Create an ATS-friendly resume, analyse its readiness,
              compare it with job descriptions and identify where your
              resume can be improved.
            </p>

            <div className="inner-page-actions resume-actions">

              <Link
                to="/resume/builder"
                className="primary-button"
              >
                Build My Resume
                <FiArrowRight />
              </Link>

              <a
                href="#resume-tools"
                className="secondary-button"
              >
                Explore Tools
              </a>

            </div>

            <div className="resume-hero-points">

              <span>
                <FiCheckCircle />
                ATS-friendly structure
              </span>

              <span>
                <FiCheckCircle />
                Job matching
              </span>

              <span>
                <FiCheckCircle />
                Wording improvement
              </span>

            </div>

          </div>


          {/* RESUME PREVIEW */}

          <div className="resume-document-preview">

            <div className="resume-preview-topbar">

              <div>
                <FiFileText />
                My Resume
              </div>

              <div className="resume-preview-actions">
                <Link
                  to="/resume/builder"
                  aria-label="Open this resume in the Resume Builder"
                  title="Open in Resume Builder"
                >
                  <FiEye />
                </Link>

                <button
                  type="button"
                  disabled
                  aria-label="Download (preview only)"
                  title="Preview only — download is available in the Resume Builder"
                >
                  <FiDownload />
                </button>
              </div>

            </div>


            <div className="resume-paper">

              <div className="resume-paper-header">

                <div>
                  <h3>Alex Morgan</h3>
                  <p>Java Full Stack Developer</p>
                </div>

                <div className="resume-contact-lines">
                  <span></span>
                  <span></span>
                  <span></span>
                </div>

              </div>

              <div className="resume-paper-section">
                <strong>SUMMARY</strong>

                <div className="resume-line long"></div>
                <div className="resume-line medium"></div>
              </div>

              <div className="resume-paper-section">
                <strong>SKILLS</strong>

                <div className="resume-skill-tags">
                  <span>Java</span>
                  <span>Spring Boot</span>
                  <span>React</span>
                  <span>SQL</span>
                </div>
              </div>

              <div className="resume-paper-section">
                <strong>PROJECTS</strong>

                <div className="resume-line long"></div>
                <div className="resume-line medium"></div>
                <div className="resume-line short"></div>
              </div>

              <div className="resume-paper-section">
                <strong>EDUCATION</strong>

                <div className="resume-line medium"></div>
                <div className="resume-line short"></div>
              </div>

            </div>


            <div className="resume-readiness-badge">

              <div>
                <FiTrendingUp />
              </div>

              <div>
                <span>Example readiness</span>
                <strong>82%</strong>
              </div>

            </div>

          </div>

        </div>

      </section>


      {/* ================= TOOL CARDS ================= */}

      <section
        className="home-section"
        id="resume-tools"
      >

        <div className="section-container">

          <div className="section-heading centered">

            <span className="section-tag">
              COMPLETE RESUME WORKSPACE
            </span>

            <h2>
              More than a resume builder
            </h2>

            <p>
              CareerAI connects resume creation with job matching,
              interview preparation and skill-gap analysis.
            </p>

          </div>


          <div className="resume-feature-grid">

            {resumeFeatures.map((feature) => (
              <Link
                to={feature.path}
                className="resume-feature-card"
                key={feature.title}
              >

                <div className="resume-feature-icon">
                  {feature.icon}
                </div>

                <h3>{feature.title}</h3>

                <p>{feature.description}</p>

                <span className="resume-card-link">
                  Open Tool <FiArrowRight />
                </span>

              </Link>
            ))}

          </div>
        </div>

      </section>


      {/* ================= ATS / JOB MATCH DEMO ================= */}

      <section className="home-section resume-analysis-section">

        <div className="section-container">

          <div className="section-heading centered">

            <span className="section-tag">
              RESUME INTELLIGENCE
            </span>

            <h2>
              Understand what your resume is communicating
            </h2>

          </div>


          <div className="resume-tool-tabs">

            <button
              className={activeTool === "ats" ? "active" : ""}
              onClick={() => setActiveTool("ats")}
            >
              ATS Analysis
            </button>

            <button
              className={activeTool === "match" ? "active" : ""}
              onClick={() => setActiveTool("match")}
            >
              Job Match
            </button>

            <button
              className={activeTool === "improve" ? "active" : ""}
              onClick={() => setActiveTool("improve")}
            >
              AI Improvement
            </button>

          </div>


          <div className="resume-analysis-card">

            <div className="analysis-summary">

              <span>
                {tools[activeTool].label}
              </span>

              <strong>
                {tools[activeTool].score}
              </strong>

              <h3>
                {tools[activeTool].title}
              </h3>

              <p>
                {tools[activeTool].description}
              </p>

            </div>


            <div className="analysis-details">

              {activeTool === "ats" && (
                <>
                  <AnalysisRow
                    label="Section Structure"
                    score={92}
                    type="good"
                  />

                  <AnalysisRow
                    label="Keyword Coverage"
                    score={78}
                    type="medium"
                  />

                  <AnalysisRow
                    label="Readability"
                    score={88}
                    type="good"
                  />

                  <AnalysisRow
                    label="Skill Coverage"
                    score={71}
                    type="medium"
                  />
                </>
              )}


              {activeTool === "match" && (
                <>
                  <SkillStatus
                    label="Java"
                    status="Matched"
                    good
                  />

                  <SkillStatus
                    label="Spring Boot"
                    status="Matched"
                    good
                  />

                  <SkillStatus
                    label="Docker"
                    status="Missing"
                  />

                  <SkillStatus
                    label="Spring Security"
                    status="Needs Improvement"
                  />
                </>
              )}


              {activeTool === "improve" && (
                <div className="improvement-list">

                  <div>
                    <FiCheckCircle />

                    <p>
                      Make the professional summary more specific to
                      your target role.
                    </p>
                  </div>

                  <div>
                    <FiCheckCircle />

                    <p>
                      Explain project responsibilities more clearly.
                    </p>
                  </div>

                  <div>
                    <FiCheckCircle />

                    <p>
                      Include technologies already used in your projects
                      where they are relevant.
                    </p>
                  </div>

                  <div>
                    <FiAlertCircle />

                    <p>
                      CareerAI should never invent experience,
                      achievements or metrics that you did not provide.
                    </p>
                  </div>

                </div>
              )}

            </div>

          </div>

        </div>

      </section>


      {/* ================= JOB MATCH ================= */}

      <section className="home-section">

        <div className="section-container split-section">

          <div className="split-content">

            <span className="section-tag">
              JOB MATCH
            </span>

            <h2>
              Compare your resume with the job you want
            </h2>

            <p>
              Add a job description and CareerAI can compare it with
              your resume to identify the skills that already match and
              the areas that may require more preparation.
            </p>

            <ul className="check-list">

              <li>
                <FiCheckCircle />
                Matched skills
              </li>

              <li>
                <FiCheckCircle />
                Missing skills
              </li>

              <li>
                <FiCheckCircle />
                Important keywords
              </li>

              <li>
                <FiCheckCircle />
                Resume relevance
              </li>

              <li>
                <FiCheckCircle />
                Interview preparation priorities
              </li>

            </ul>

          </div>


          <div className="job-match-card">

            <div className="job-match-header">

              <div>
                <span>EXAMPLE JOB MATCH</span>
                <h3>Backend Developer</h3>
              </div>

              <div className="job-match-score">
                76%
              </div>

            </div>


            <div className="job-match-section">

              <span>MATCHED SKILLS</span>

              <div className="job-skill-list good">

                <span>Java</span>
                <span>Spring Boot</span>
                <span>REST APIs</span>
                <span>MySQL</span>

              </div>

            </div>


            <div className="job-match-section">

              <span>SKILLS TO IMPROVE</span>

              <div className="job-skill-list warning">

                <span>Docker</span>
                <span>AWS</span>
                <span>Spring Security</span>

              </div>

            </div>


            <div className="job-match-next">

              <FiTarget />

              <div>
                <span>Preparation priority</span>

                <strong>
                  Docker + Spring Security
                </strong>

              </div>

            </div>

          </div>

        </div>

      </section>


      {/* ================= RESUME VERSIONS ================= */}

      <section className="home-section resume-version-section">

        <div className="section-container split-section">

          <div className="resume-versions-preview">

            <div className="resume-version-header">

              <div>
                <span>MY RESUMES</span>
                <h3>Resume Versions</h3>
              </div>

              <button>
                <FiPlus />
                New Version
              </button>

            </div>


            <div className="resume-version active">

              <FiFileText />

              <div>
                <strong>Backend Developer Resume</strong>
                <span>Updated recently</span>
              </div>

              <span className="version-current">
                Current
              </span>

            </div>


            <div className="resume-version">

              <FiFileText />

              <div>
                <strong>Java Developer Resume</strong>
                <span>Previous version</span>
              </div>

            </div>


            <div className="resume-version">

              <FiFileText />

              <div>
                <strong>General Resume</strong>
                <span>Original version</span>
              </div>

            </div>

          </div>


          <div className="split-content">

            <span className="section-tag">
              RESUME VERSIONS
            </span>

            <h2>
              Keep different resumes for different opportunities
            </h2>

            <p>
              CareerAI can maintain resume versions so you can improve
              your resume over time without losing earlier versions.
            </p>

            <ul className="check-list">

              <li>
                <FiCheckCircle />
                Create multiple resume versions
              </li>

              <li>
                <FiCheckCircle />
                Track updated versions
              </li>

              <li>
                <FiCheckCircle />
                Use job-specific resumes
              </li>

              <li>
                <FiCheckCircle />
                Compare improvements over time
              </li>

            </ul>

          </div>

        </div>

      </section>


      {/* ================= CTA ================= */}

      <section className="resume-page-cta-section">

        <div className="section-container">

          <div className="resume-page-cta">

            <div>

              <span className="section-tag light">
                BUILD YOUR RESUME
              </span>

              <h2>
                Turn your real experience into a stronger resume.
              </h2>

              <p>
                CareerAI helps you present your actual skills and
                experience clearly without inventing information.
              </p>

            </div>

            <Link
              to="/resume/builder"
              className="cta-white-button"
            >
              Start Building
              <FiArrowRight />
            </Link>

          </div>

        </div>

      </section>

    </div>
  );
}


function AnalysisRow({ label, score, type }) {
  return (
    <div className="analysis-row">

      <div>
        <span>{label}</span>
        <strong>{score}%</strong>
      </div>

      <div className="analysis-progress">
        <div
          className={type}
          style={{ width: `${score}%` }}
        ></div>
      </div>

    </div>
  );
}


function SkillStatus({ label, status, good }) {
  return (
    <div className="skill-status-row">

      <span>{label}</span>

      <strong className={good ? "good" : "warning"}>
        {good ? <FiCheckCircle /> : <FiAlertCircle />}
        {status}
      </strong>

    </div>
  );
}

export default ResumeTools;