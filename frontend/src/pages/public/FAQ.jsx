import { useState } from "react";
import { Link } from "react-router-dom";

import {
  FiSearch,
  FiChevronDown,
  FiChevronUp,
  FiX,
  FiArrowRight,
  FiHelpCircle
} from "react-icons/fi";

const categories = [
  "All",
  "General",
  "Practice",
  "AI Interview",
  "Resume Tools",
  "Career Tracks",
  "Account & Plans",
  "Privacy & AI"
];

// answer: a paragraph; list (optional): bullet points shown after it.
const faqs = [
  {
    id: "what-is-careerai",
    category: "General",
    question: "What is CareerAI?",
    answer: "CareerAI is a career-preparation platform that combines role-based learning, practice, resume tools, interview preparation and performance feedback."
  },
  {
    id: "who-is-it-for",
    category: "General",
    question: "Who is CareerAI designed for?",
    answer: "Students, freshers, working professionals and career switchers can use the platform to prepare for different career goals."
  },
  {
    id: "practice-types",
    category: "Practice",
    question: "What types of practice are available?",
    answer: "CareerAI includes eight practice modules:",
    list: [
      "Technical questions",
      "Aptitude",
      "Coding",
      "SQL",
      "System Design",
      "Voice Practice",
      "Daily Challenge",
      "Mock Assessments"
    ]
  },
  {
    id: "same-questions",
    category: "Practice",
    question: "Are the practice questions always the same?",
    answer: "The current frontend uses demo content. The final question engine is designed to support stored questions and dynamically generated questions based on topic, difficulty and performance."
  },
  {
    id: "ai-interview-flow",
    category: "AI Interview",
    question: "How does the AI Interview work?",
    answer: "Users configure a role-based, resume-based or job-based interview, complete a device check, answer interview questions and receive structured feedback."
  },
  {
    id: "voice-live",
    category: "AI Interview",
    question: "Is the voice interviewer already live?",
    answer: "Not yet. The current frontend demonstrates the interview flow. Real-time voice and avatar services will be connected later."
  },
  {
    id: "resume-features",
    category: "Resume Tools",
    question: "What can CareerAI do with my resume?",
    answer: "The Resume Tools area includes:",
    list: [
      "Resume Builder",
      "ATS Analyzer",
      "Job Match",
      "AI Wording Improvement",
      "Resume Versions",
      "Interview Preparation"
    ]
  },
  {
    id: "resume-invent",
    category: "Resume Tools",
    question: "Does CareerAI invent experience for my resume?",
    answer: "No. CareerAI should improve wording and presentation without inventing qualifications, experience, achievements or metrics."
  },
  {
    id: "what-is-track",
    category: "Career Tracks",
    question: "What is a Career Track?",
    answer: "A Career Track is a structured role-based roadmap showing relevant skills, preparation topics, projects and interview focus areas."
  },
  {
    id: "change-role",
    category: "Career Tracks",
    question: "Can I change my target role later?",
    answer: "Yes. CareerAI is designed so the target career can be updated and the preparation plan can adapt."
  },
  {
    id: "no-account",
    category: "Account & Plans",
    question: "Can I use CareerAI without creating an account?",
    answer: "Public pages and limited demo functionality can be explored without a full account. Registered users can later save progress, history and personalized preparation."
  },
  {
    id: "plans",
    category: "Account & Plans",
    question: "Will CareerAI have free and paid plans?",
    answer: "CareerAI is designed to support guest, free and premium access levels. Final subscription limits and pricing will be configured when the backend and payment system are completed."
  },
  {
    id: "api-keys",
    category: "Privacy & AI",
    question: "Are API keys stored in the frontend?",
    answer: "No. Private AI and service credentials should be stored securely on the backend and never exposed in browser code."
  },
  {
    id: "ai-correct",
    category: "Privacy & AI",
    question: "Is AI feedback always correct?",
    answer: "AI feedback is intended to support learning and preparation. Users should review important recommendations rather than treating generated feedback as guaranteed fact."
  }
];

function matchesSearch(item, query) {
  if (!query) return true;

  const haystack = [item.question, item.answer, item.category, ...(item.list || [])]
    .join(" ")
    .toLowerCase();

  return haystack.includes(query);
}


function FAQ() {
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("All");
  const [openIds, setOpenIds] = useState(() => new Set([faqs[0].id]));

  const query = search.trim().toLowerCase();

  const visible = faqs.filter((item) =>
    (category === "All" || item.category === category) && matchesSearch(item, query)
  );

  const countFor = (name) =>
    faqs.filter((item) => (name === "All" || item.category === name) && matchesSearch(item, query)).length;

  const toggle = (id) => {
    setOpenIds((current) => {
      const next = new Set(current);

      if (next.has(id)) next.delete(id);
      else next.add(id);

      return next;
    });
  };

  const clearFilters = () => {
    setSearch("");
    setCategory("All");
  };

  return (
    <div className="faq-page">

      {/* HERO */}

      <section className="faq-hero">

        <div className="section-container faq-hero-inner">

          <span className="section-tag">
            FREQUENTLY ASKED QUESTIONS
          </span>

          <h1>Find answers about CareerAI</h1>

          <p>
            Learn how CareerAI's practice tools, resume features, AI
            interviews, career tracks and account system are designed to work.
          </p>

          <div className="faq-search">
            <label
              htmlFor="faq-search"
              className="sr-only"
            >
              Search questions
            </label>
            <FiSearch aria-hidden="true" />
            <input
              id="faq-search"
              type="search"
              placeholder="Search questions..."
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
            {search && (
              <button
                type="button"
                className="faq-search-clear"
                onClick={() => setSearch("")}
                aria-label="Clear search"
              >
                <FiX aria-hidden="true" />
              </button>
            )}
          </div>

        </div>

      </section>


      <section className="home-section faq-body">

        <div className="section-container faq-container">

          {/* CATEGORY FILTERS */}

          <div
            className="faq-categories"
            role="group"
            aria-label="Filter questions by category"
          >
            {categories.map((name) => (
              <button
                key={name}
                type="button"
                className={`faq-category${category === name ? " active" : ""}`}
                aria-pressed={category === name}
                onClick={() => setCategory(name)}
              >
                {name}
                <span>{countFor(name)}</span>
              </button>
            ))}
          </div>

          <p
            className="faq-result-count"
            aria-live="polite"
          >
            {visible.length} {visible.length === 1 ? "question" : "questions"}
            {category !== "All" && ` in ${category}`}
            {query && ` matching "${search.trim()}"`}
          </p>


          {/* ACCORDION */}

          {visible.length > 0 ? (
            <div className="faq-list">

              {visible.map((item) => {
                const isOpen = openIds.has(item.id);

                return (
                  <div
                    key={item.id}
                    className={`faq-item${isOpen ? " open" : ""}`}
                  >

                    <h2 className="faq-question">
                      <button
                        type="button"
                        id={`faq-q-${item.id}`}
                        aria-expanded={isOpen}
                        aria-controls={`faq-a-${item.id}`}
                        onClick={() => toggle(item.id)}
                      >
                        <span className="faq-question-text">
                          <small>{item.category}</small>
                          {item.question}
                        </span>
                        <span
                          className="faq-chevron"
                          aria-hidden="true"
                        >
                          {isOpen ? <FiChevronUp /> : <FiChevronDown />}
                        </span>
                      </button>
                    </h2>

                    <div
                      id={`faq-a-${item.id}`}
                      role="region"
                      aria-labelledby={`faq-q-${item.id}`}
                      className="faq-answer"
                      inert={!isOpen}
                    >
                      <div className="faq-answer-inner">
                        <p>{item.answer}</p>
                        {item.list && (
                          <ul>
                            {item.list.map((point) => (
                              <li key={point}>{point}</li>
                            ))}
                          </ul>
                        )}
                      </div>
                    </div>

                  </div>
                );
              })}

            </div>
          ) : (
            <div className="faq-empty">
              <FiHelpCircle aria-hidden="true" />
              <p>No matching questions found.</p>
              <button
                type="button"
                className="secondary-button"
                onClick={clearFilters}
              >
                Clear search and filters
              </button>
            </div>
          )}

        </div>

      </section>


      {/* STILL NEED HELP */}

      <section className="info-page-cta-section">

        <div className="section-container">

          <div className="info-page-cta">

            <div>
              <span className="section-tag light">
                STILL NEED HELP
              </span>
              <h2>Still have a question?</h2>
            </div>

            <div className="info-page-cta-actions">

              <Link
                to="/contact"
                className="cta-white-button"
              >
                Contact CareerAI
                <FiArrowRight aria-hidden="true" />
              </Link>

              <Link
                to="/resources"
                className="info-page-cta-outline"
              >
                Explore Resources
              </Link>

            </div>

          </div>

        </div>

      </section>

    </div>
  );
}

export default FAQ;
