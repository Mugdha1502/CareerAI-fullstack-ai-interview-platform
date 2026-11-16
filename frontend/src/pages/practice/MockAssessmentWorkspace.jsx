import { useEffect, useRef, useState } from "react";
import {
  FiClock,
  FiArrowLeft,
  FiArrowRight,
  FiFlag,
  FiSend,
  FiFileText,
  FiCheckCircle
} from "react-icons/fi";
import { buildAssessment } from "./practiceProvider";
import { formatClock, percentage, summarizeAreas } from "./practiceUtils";

function MockAssessmentWorkspace({ config, onComplete }) {
  const [stage, setStage] = useState("instructions");

  const count = Number(config.count) || 10;
  const durationMinutes = Number(config.duration) || 15;

  if (stage === "instructions") {
    return (
      <section className="assessment-instructions">

        <div className="assessment-instructions-head">
          <FiFileText />
          <div>
            <span className="quiz-runner-heading">Before you begin</span>
            <h2>{config.topic}</h2>
          </div>
        </div>

        <div className="assessment-facts">
          <div>
            <span>Questions</span>
            <strong>{count}</strong>
          </div>
          <div>
            <span>Duration</span>
            <strong>{durationMinutes} minutes</strong>
          </div>
          <div>
            <span>Difficulty</span>
            <strong>{config.difficulty}</strong>
          </div>
        </div>

        <ul className="assessment-rules">
          <li>Assessment is timed and submits automatically when time runs out.</li>
          <li>Answers can be changed before submission.</li>
          <li>Use the question navigator to move between questions or flag them for review.</li>
          <li>Final score appears after submission.</li>
        </ul>

        <button
          type="button"
          className="primary-button"
          onClick={() => setStage("exam")}
        >
          Begin Assessment
          <FiArrowRight />
        </button>

      </section>
    );
  }

  return (
    <AssessmentExam
      config={config}
      count={count}
      durationMinutes={durationMinutes}
      onComplete={onComplete}
    />
  );
}


function AssessmentExam({ config, count, durationMinutes, onComplete }) {
  // TODO backend: POST /api/practice/assessments to receive a server-generated paper.
  const [questions] = useState(() =>
    buildAssessment({
      assessmentType: config.topic,
      difficulty: config.difficulty,
      count
    })
  );
  const [answers, setAnswers] = useState(() => Array(questions.length).fill(null));
  const [flags, setFlags] = useState(() => Array(questions.length).fill(false));
  const [current, setCurrent] = useState(0);
  const [secondsLeft, setSecondsLeft] = useState(durationMinutes * 60);
  const submittedRef = useRef(false);

  const submit = (finalAnswers) => {
    if (submittedRef.current) return;

    submittedRef.current = true;

    const history = questions.map((question, index) => ({
      tag: question.tag,
      category: question.category,
      correct: finalAnswers[index] === question.answerIndex
    }));

    const correct = history.filter((item) => item.correct).length;

    const categories = [...new Set(questions.map((question) => question.category))];
    const breakdown = categories.map((category) => {
      const items = history.filter((item) => item.category === category);
      return {
        label: category,
        value: percentage(items.filter((item) => item.correct).length, items.length)
      };
    });

    const usedSeconds = durationMinutes * 60 - secondsLeft;

    onComplete({
      kicker: config.topic.toUpperCase(),
      title: "Assessment Complete",
      score: correct,
      total: questions.length,
      stats: [
        { label: "Time Used", value: formatClock(usedSeconds) },
        { label: "Answered", value: `${finalAnswers.filter((value) => value !== null).length} / ${questions.length}` }
      ],
      breakdown,
      ...summarizeAreas(history),
      xpActivity: "MOCK_ASSESSMENT"
    });
  };

  useEffect(() => {
    const timer = setInterval(() => {
      setSecondsLeft((value) => Math.max(value - 1, 0));
    }, 1000);

    return () => clearInterval(timer);
  }, []);

  // Auto-submit when time runs out.
  useEffect(() => {
    if (secondsLeft === 0) {
      submit(answers);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [secondsLeft]);

  const question = questions[current];
  const answeredCount = answers.filter((value) => value !== null).length;

  const choose = (optionIndex) => {
    setAnswers((items) => items.map((value, index) => (index === current ? optionIndex : value)));
  };

  const toggleFlag = () => {
    setFlags((items) => items.map((value, index) => (index === current ? !value : value)));
  };

  const handleSubmit = () => {
    const unanswered = questions.length - answeredCount;
    const message = unanswered > 0
      ? `You have ${unanswered} unanswered question${unanswered === 1 ? "" : "s"}. Submit the assessment anyway?`
      : "Submit the assessment? You will not be able to change your answers.";

    if (window.confirm(message)) {
      submit(answers);
    }
  };

  return (
    <div className="assessment-layout">

      <section className="assessment-main">

        <div className="assessment-topbar">

          <div>
            <span className="quiz-runner-heading">{config.topic}</span>
            <strong>
              Question {current + 1} of {questions.length}
            </strong>
          </div>

          <span className={`assessment-timer${secondsLeft <= 60 ? " warning" : ""}`}>
            <FiClock />
            Time Remaining {formatClock(secondsLeft)}
          </span>

        </div>


        <div className="quiz-question-card assessment-question">

          <div className="assessment-question-meta">
            <span className="quiz-question-tag">{question.category}</span>
            <button
              type="button"
              className={`assessment-flag${flags[current] ? " active" : ""}`}
              aria-pressed={flags[current]}
              onClick={toggleFlag}
            >
              <FiFlag />
              {flags[current] ? "Flagged" : "Flag for review"}
            </button>
          </div>

          <h3>{question.question}</h3>

          {question.table && (
            <div className="practice-table-wrap">
              <table className="practice-data-table">
                <thead>
                  <tr>
                    {question.table.headers.map((header) => (
                      <th key={header}>{header}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {question.table.rows.map((row) => (
                    <tr key={row.join("-")}>
                      {row.map((cell, index) => (
                        <td key={index}>{cell}</td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div
            className="quiz-options"
            role="radiogroup"
            aria-label="Answer options"
          >
            {question.options.map((option, optionIndex) => (
              <button
                type="button"
                key={option}
                role="radio"
                aria-checked={answers[current] === optionIndex}
                className={`quiz-option${answers[current] === optionIndex ? " selected" : ""}`}
                onClick={() => choose(optionIndex)}
              >
                <span className="quiz-option-letter">
                  {String.fromCharCode(65 + optionIndex)}
                </span>
                <span>{option}</span>
              </button>
            ))}
          </div>

          <div className="assessment-nav-buttons">

            <button
              type="button"
              className="secondary-button"
              onClick={() => setCurrent((value) => value - 1)}
              disabled={current === 0}
            >
              <FiArrowLeft />
              Previous
            </button>

            <button
              type="button"
              className="secondary-button"
              onClick={() => setCurrent((value) => value + 1)}
              disabled={current === questions.length - 1}
            >
              Next
              <FiArrowRight />
            </button>

          </div>

        </div>

      </section>


      <aside className="assessment-sidebar">

        <h3>Question Navigator</h3>

        <p className="assessment-progress-text">
          <FiCheckCircle />
          {answeredCount} of {questions.length} answered
        </p>

        <div
          className="assessment-navigator"
          role="list"
        >
          {questions.map((item, index) => {
            const states = [
              index === current ? "current" : "",
              answers[index] !== null ? "answered" : "",
              flags[index] ? "flagged" : ""
            ].filter(Boolean).join(" ");

            return (
              <button
                type="button"
                role="listitem"
                key={item.id}
                className={`assessment-nav-item ${states}`}
                aria-label={`Question ${index + 1}${answers[index] !== null ? ", answered" : ""}${flags[index] ? ", flagged" : ""}`}
                aria-current={index === current ? "true" : undefined}
                onClick={() => setCurrent(index)}
              >
                {index + 1}
              </button>
            );
          })}
        </div>

        <ul className="assessment-legend">
          <li><span className="answered"></span>Answered</li>
          <li><span className="flagged"></span>Flagged</li>
          <li><span></span>Not answered</li>
        </ul>

        <button
          type="button"
          className="primary-button assessment-submit"
          onClick={handleSubmit}
        >
          <FiSend />
          Submit Assessment
        </button>

      </aside>

    </div>
  );
}

export default MockAssessmentWorkspace;
