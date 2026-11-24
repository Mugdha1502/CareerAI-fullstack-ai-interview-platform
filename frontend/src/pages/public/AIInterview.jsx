import { useState } from "react";
import { Link } from "react-router-dom";

import {
  FiMic,
  FiVideo,
  FiFileText,
  FiBriefcase,
  FiTarget,
  FiMessageSquare,
  FiBarChart2,
  FiCheckCircle,
  FiArrowRight,
  FiPlay,
  FiVolume2,
  FiCpu,
  FiActivity,
  FiClock,
  FiUser
} from "react-icons/fi";

function AIInterview() {
  const [activeType, setActiveType] = useState("role");

  const interviewTypes = {
    role: {
      title: "Role-Based Interview",
      description:
        "Choose your target role and practise questions relevant to the technologies and skills required for that career.",
      icon: <FiTarget />,
      example: "Java Full Stack Developer",
      questions: [
        "Explain dependency injection in Spring Boot.",
        "How would you design authentication for a REST API?",
        "What is the difference between INNER JOIN and LEFT JOIN?"
      ]
    },

    resume: {
      title: "Resume-Based Interview",
      description:
        "CareerAI uses information from your resume to ask questions about your real skills, projects, technologies and experience.",
      icon: <FiFileText />,
      example: "Bean Boutique Coffee Shop",
      questions: [
        "Explain how your React frontend communicates with Spring Boot.",
        "Why did you choose MySQL for this project?",
        "How did you handle user authentication?"
      ]
    },

    job: {
      title: "Job-Based Interview",
      description:
        "Combine your resume with a job description to practise an interview focused on the requirements of a specific opportunity.",
      icon: <FiBriefcase />,
      example: "Junior Backend Developer",
      questions: [
        "How comfortable are you building REST APIs?",
        "What experience do you have with Spring Security?",
        "How would you improve API performance?"
      ]
    }
  };

  const flowSteps = [
    {
      icon: <FiTarget />,
      title: "Configure Interview",
      text: "Choose your role, interview type, difficulty and duration."
    },
    {
      icon: <FiVideo />,
      title: "Device Check",
      text: "Check microphone, camera, speaker and connection before starting."
    },
    {
      icon: <FiMic />,
      title: "Start Conversation",
      text: "Answer questions naturally through a real-time voice interview."
    },
    {
      icon: <FiMessageSquare />,
      title: "Adaptive Follow-Ups",
      text: "The interviewer can continue with relevant follow-up questions."
    },
    {
      icon: <FiBarChart2 />,
      title: "Receive Report",
      text: "Review scores, strengths, weak areas and suggested improvements."
    }
  ];

  return (
    <div className="ai-interview-page">

      {/* ================= HERO ================= */}

      <section className="ai-interview-hero">

        <div className="ai-hero-glow ai-hero-glow-one"></div>
        <div className="ai-hero-glow ai-hero-glow-two"></div>

        <div className="section-container ai-interview-hero-grid">

          <div className="ai-interview-hero-content">

            <span className="ai-dark-tag">
              CAREERAI AI INTERVIEW
            </span>

            <h1>
              Practise interviews that
              <span> respond to you.</span>
            </h1>

            <p>
              Prepare for technical, HR and behavioural interviews
              through a conversational AI interview experience designed
              around your role, resume or target job.
            </p>

            <div className="ai-hero-actions">

              <Link
                to="/ai-interview/setup"
                className="ai-primary-button"
              >
                Start AI Interview
                <FiArrowRight />
              </Link>

              <a href="#how-ai-interview-works" className="ai-secondary-button">
                <FiPlay />
                See How It Works
              </a>

            </div>

            <div className="ai-hero-benefits">

              <span>
                <FiCheckCircle />
                Adaptive questions
              </span>

              <span>
                <FiCheckCircle />
                Voice interaction
              </span>

              <span>
                <FiCheckCircle />
                Detailed feedback
              </span>

            </div>

          </div>


          {/* INTERVIEW ROOM PREVIEW */}

          <div className="ai-room-preview">

            <div className="ai-room-topbar">

              <div className="ai-room-title">
                <span className="ai-live-dot"></span>
                AI Interview
              </div>

              <div className="ai-room-time">
                <FiClock />
                12:45
              </div>

            </div>

            <div className="ai-room-main">

              <div className="ai-avatar-wrapper">

                <div className="ai-avatar-ring"></div>

                <div className="ai-avatar">
                  AI
                </div>

              </div>

              <h3>CareerAI Interviewer</h3>

              <span className="ai-state">
                <span></span>
                Listening to your answer
              </span>

            </div>

            <div className="ai-question-card">

              <span>CURRENT QUESTION</span>

              <p>
                Explain how authentication works in your Spring Boot
                application.
              </p>

            </div>

            <div className="ai-transcript-preview">

              <div>
                <strong>Candidate</strong>

                <p>
                  I used Spring Security together with JWT authentication...
                </p>
              </div>

              <div>
                <strong>AI Interviewer</strong>

                <p>
                  Why would JWT be useful for a REST-based application?
                </p>
              </div>

            </div>

            <div className="ai-room-controls">

              <button
                type="button"
                aria-label="Microphone (preview)"
              >
                <FiMic />
              </button>

              <button
                type="button"
                aria-label="Camera (preview)"
              >
                <FiVideo />
              </button>

              <button
                type="button"
                aria-label="Speaker (preview)"
              >
                <FiVolume2 />
              </button>

              <span
                className="ai-end-button ai-preview-only"
                aria-label="Interview room preview only"
              >
                Preview Only
              </span>

            </div>

          </div>

        </div>

      </section>


      {/* ================= TYPES ================= */}

      <section className="home-section">

        <div className="section-container">

          <div className="section-heading centered">

            <span className="section-tag">
              PERSONALIZED INTERVIEWS
            </span>

            <h2>
              Choose what your interview should be based on
            </h2>

            <p>
              CareerAI supports different interview sources so your
              preparation can match your actual goal.
            </p>

          </div>


          <div className="interview-type-tabs">

            <button
              className={activeType === "role" ? "active" : ""}
              onClick={() => setActiveType("role")}
            >
              <FiTarget />
              Role-Based
            </button>

            <button
              className={activeType === "resume" ? "active" : ""}
              onClick={() => setActiveType("resume")}
            >
              <FiFileText />
              Resume-Based
            </button>

            <button
              className={activeType === "job" ? "active" : ""}
              onClick={() => setActiveType("job")}
            >
              <FiBriefcase />
              Job-Based
            </button>

          </div>


          <div className="interview-type-content">

            <div className="interview-type-info">

              <div className="interview-type-icon">
                {interviewTypes[activeType].icon}
              </div>

              <span className="section-tag">
                INTERVIEW MODE
              </span>

              <h3>
                {interviewTypes[activeType].title}
              </h3>

              <p>
                {interviewTypes[activeType].description}
              </p>

              <div className="interview-example-label">
                Example
              </div>

              <strong className="interview-example-value">
                {interviewTypes[activeType].example}
              </strong>

            </div>


            <div className="sample-questions-card">

              <span>SAMPLE QUESTIONS</span>

              {interviewTypes[activeType].questions.map(
                (question, index) => (
                  <div
                    className="sample-question"
                    key={question}
                  >
                    <span>
                      {String(index + 1).padStart(2, "0")}
                    </span>

                    <p>{question}</p>
                  </div>
                )
              )}

            </div>

          </div>


          <div className="interview-type-action">

            <Link
              to={`/ai-interview/setup?type=${activeType}`}
              className="primary-button"
            >
              Start {interviewTypes[activeType].title}
              <FiArrowRight />
            </Link>

          </div>

        </div>

      </section>


      {/* ================= HOW IT WORKS ================= */}

      <section
        className="home-section interview-flow-section"
        id="how-ai-interview-works"
      >

        <div className="section-container">

          <div className="section-heading centered">

            <span className="section-tag">
              INTERVIEW PROCESS
            </span>

            <h2>
              From setup to detailed feedback
            </h2>

            <p>
              CareerAI guides you through the complete mock interview
              process instead of simply showing a list of questions.
            </p>

          </div>


          <div className="interview-flow-grid">

            {flowSteps.map((step, index) => (
              <div
                className="interview-flow-card"
                key={step.title}
              >

                <div className="interview-flow-number">
                  {String(index + 1).padStart(2, "0")}
                </div>

                <div className="interview-flow-icon">
                  {step.icon}
                </div>

                <h3>{step.title}</h3>

                <p>{step.text}</p>

              </div>
            ))}

          </div>


          <div className="interview-flow-action">

            <Link
              to="/ai-interview/setup"
              className="primary-button"
            >
              Start Interview Setup
              <FiArrowRight />
            </Link>

          </div>

        </div>

      </section>


      {/* ================= ADAPTIVE INTERVIEW ================= */}

      <section className="home-section">

        <div className="section-container split-section">

          <div className="split-content">

            <span className="section-tag">
              ADAPTIVE CONVERSATION
            </span>

            <h2>
              The interview can follow your answer
            </h2>

            <p>
              Instead of reading questions from a fixed list, the final
              CareerAI interview system is designed to understand the
              current conversation and ask relevant follow-up questions.
            </p>

            <ul className="check-list">

              <li>
                <FiCheckCircle />
                Understand the current answer
              </li>

              <li>
                <FiCheckCircle />
                Ask relevant follow-up questions
              </li>

              <li>
                <FiCheckCircle />
                Adjust questions to interview context
              </li>

              <li>
                <FiCheckCircle />
                Maintain the current interview conversation
              </li>

            </ul>

          </div>


          <div className="adaptive-conversation-card">

            <div className="conversation-message ai-message">

              <div className="conversation-avatar">
                AI
              </div>

              <div>
                <span>AI Interviewer</span>

                <p>
                  Tell me about a project where you used Spring Boot.
                </p>
              </div>

            </div>


            <div className="conversation-message candidate-message">

              <div className="conversation-avatar candidate">
                <FiUser />
              </div>

              <div>
                <span>You</span>

                <p>
                  I developed REST APIs for a web application using
                  Spring Boot.
                </p>
              </div>

            </div>


            <div className="conversation-thinking">

              <FiCpu />

              <span>
                CareerAI understands the answer...
              </span>

            </div>


            <div className="conversation-message ai-message">

              <div className="conversation-avatar">
                AI
              </div>

              <div>
                <span>AI Interviewer</span>

                <p>
                  How did you handle exceptions in those REST APIs?
                </p>
              </div>

            </div>

          </div>

        </div>

      </section>


      {/* ================= REPORT ================= */}

      <section className="home-section interview-report-section">

        <div className="section-container split-section">

          <div className="report-preview-card">

            <div className="report-preview-header">

              <div>
                <span>INTERVIEW REPORT</span>
                <h3>Java Full Stack Interview</h3>
              </div>

              <div className="report-score">
                82
                <span>/100</span>
              </div>

            </div>

            <div className="report-score-grid">

              <div>
                <span>Technical</span>
                <strong>84%</strong>
              </div>

              <div>
                <span>Communication</span>
                <strong>79%</strong>
              </div>

              <div>
                <span>Relevance</span>
                <strong>86%</strong>
              </div>

              <div>
                <span>Problem Solving</span>
                <strong>80%</strong>
              </div>

            </div>

            <div className="report-insight">

              <div className="report-insight-icon">
                <FiActivity />
              </div>

              <div>
                <span>Focus area</span>
                <strong>Spring Security</strong>

                <p>
                  Review authentication, authorization and JWT concepts
                  before your next interview.
                </p>
              </div>

            </div>

          </div>


          <div className="split-content">

            <span className="section-tag">
              INTERVIEW FEEDBACK
            </span>

            <h2>
              Learn from every interview
            </h2>

            <p>
              After the interview, CareerAI can turn the conversation
              into structured feedback that helps identify what went
              well and what needs more preparation.
            </p>

            <ul className="check-list">

              <li>
                <FiCheckCircle />
                Overall and category scores
              </li>

              <li>
                <FiCheckCircle />
                Question-by-question analysis
              </li>

              <li>
                <FiCheckCircle />
                Strengths and weak areas
              </li>

              <li>
                <FiCheckCircle />
                Suggested answer improvements
              </li>

              <li>
                <FiCheckCircle />
                Recommended next preparation steps
              </li>

            </ul>

          </div>

        </div>

      </section>


      {/* ================= CTA ================= */}

      <section className="ai-page-cta-section">

        <div className="section-container">

          <div className="ai-page-cta">

            <div>

              <span className="ai-dark-tag">
                PRACTISE BEFORE THE REAL INTERVIEW
              </span>

              <h2>
                Build confidence through realistic interview practice.
              </h2>

              <p>
                Create your CareerAI profile and prepare for interviews
                based on the role you actually want.
              </p>

            </div>

            <Link
              to="/ai-interview/setup"
              className="cta-white-button"
            >
              Start AI Interview
              <FiArrowRight />
            </Link>

          </div>

        </div>

      </section>

    </div>
  );
}

export default AIInterview;