import { useEffect, useState } from "react";
import {
  FiCheckCircle,
  FiXCircle,
  FiArrowRight,
  FiClock
} from "react-icons/fi";
import { formatClock, percentage } from "./practiceUtils";

/**
 * Shared multiple-choice runner (Technical quiz, Aptitude, Daily Challenge).
 *
 * questions: [{ id, question, options[], answerIndex, explanation, tag, table, category }]
 * onFinish({ correct, total, history, seconds })
 */
function QuizRunner({ questions, heading, showStats = false, showTimer = false, finishLabel = "Finish", onFinish }) {
  const [index, setIndex] = useState(0);
  const [selected, setSelected] = useState(null);
  const [checked, setChecked] = useState(false);
  const [history, setHistory] = useState([]);
  const [seconds, setSeconds] = useState(0);

  useEffect(() => {
    if (!showTimer) {
      return undefined;
    }

    const timer = setInterval(() => setSeconds((value) => value + 1), 1000);

    return () => clearInterval(timer);
  }, [showTimer]);

  if (questions.length === 0) {
    return (
      <div className="practice-empty-state">
        <p>No questions are available for this selection yet.</p>
      </div>
    );
  }

  const question = questions[index];
  const isLast = index === questions.length - 1;
  const correctCount = history.filter((item) => item.correct).length;
  const incorrectCount = history.length - correctCount;
  const isCorrect = selected === question.answerIndex;

  const handleCheck = () => {
    setChecked(true);
    setHistory((items) => [
      ...items,
      { id: question.id, tag: question.tag, category: question.category, correct: isCorrect }
    ]);
  };

  const handleNext = () => {
    if (isLast) {
      onFinish({ correct: correctCount, total: questions.length, history, seconds });
      return;
    }

    setIndex((value) => value + 1);
    setSelected(null);
    setChecked(false);
  };

  return (
    <div className="quiz-runner">

      <div className="quiz-runner-header">

        <div>
          {heading && <span className="quiz-runner-heading">{heading}</span>}
          <strong>
            Question {index + 1} of {questions.length}
          </strong>
        </div>

        <div className="quiz-runner-meta">

          {showStats && (
            <>
              <span className="quiz-stat correct">
                <FiCheckCircle />
                {correctCount} correct
              </span>
              <span className="quiz-stat incorrect">
                <FiXCircle />
                {incorrectCount} incorrect
              </span>
              <span className="quiz-stat">
                {percentage(correctCount, history.length)}% accuracy
              </span>
            </>
          )}

          {showTimer && (
            <span className="quiz-stat">
              <FiClock />
              {formatClock(seconds)}
            </span>
          )}

        </div>

      </div>


      <div
        className="practice-progress-track"
        role="progressbar"
        aria-label="Question progress"
        aria-valuenow={index + 1}
        aria-valuemin={1}
        aria-valuemax={questions.length}
      >
        <div style={{ width: `${percentage(index + (checked ? 1 : 0), questions.length)}%` }}></div>
      </div>


      <div className="quiz-question-card">

        {question.tag && (
          <span className="quiz-question-tag">{question.tag}</span>
        )}

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
                    {row.map((cell, cellIndex) => (
                      <td key={cellIndex}>{cell}</td>
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
          {question.options.map((option, optionIndex) => {
            let state = "";

            if (checked && optionIndex === question.answerIndex) state = " correct";
            else if (checked && optionIndex === selected) state = " incorrect";
            else if (optionIndex === selected) state = " selected";

            return (
              <button
                type="button"
                key={option}
                role="radio"
                aria-checked={optionIndex === selected}
                className={`quiz-option${state}`}
                onClick={() => !checked && setSelected(optionIndex)}
                disabled={checked}
              >
                <span className="quiz-option-letter">
                  {String.fromCharCode(65 + optionIndex)}
                </span>
                <span>{option}</span>
              </button>
            );
          })}
        </div>


        {checked && (
          <div
            className={`quiz-feedback ${isCorrect ? "correct" : "incorrect"}`}
            aria-live="polite"
          >
            <strong>
              {isCorrect ? <FiCheckCircle /> : <FiXCircle />}
              {isCorrect
                ? "Correct ✓"
                : `Incorrect — Correct answer: ${question.options[question.answerIndex]}`}
            </strong>
            <p>{question.explanation}</p>
          </div>
        )}


        <div className="quiz-actions">

          {!checked ? (
            <button
              type="button"
              className="primary-button"
              onClick={handleCheck}
              disabled={selected === null}
            >
              Check Answer
            </button>
          ) : (
            <button
              type="button"
              className="primary-button"
              onClick={handleNext}
            >
              {isLast ? finishLabel : "Next Question"}
              <FiArrowRight />
            </button>
          )}

        </div>

      </div>

    </div>
  );
}

export default QuizRunner;
