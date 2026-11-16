import { useState } from "react";
import {
  FiBookOpen,
  FiCheckCircle,
  FiAlertTriangle,
  FiStar,
  FiCode,
  FiArrowLeft,
  FiArrowRight,
  FiCheck,
  FiXCircle,
  FiSend
} from "react-icons/fi";
import QuizRunner from "./QuizRunner";
import {
  evaluatePracticeAnswer,
  getStudyNotes
} from "./practiceProvider";
import { usePracticeQuestions } from "./usePracticeQuestions";
import { PracticeLoading, PracticeSourceNotice } from "./PracticeSourceNotice";
import { summarizeAreas } from "./practiceUtils";

function TechnicalWorkspace({ config, onComplete }) {
  if (config.practiceType === "notes") {
    return <NotesMode config={config} onComplete={onComplete} />;
  }

  if (config.practiceType === "descriptive") {
    return <DescriptiveMode config={config} onComplete={onComplete} />;
  }

  return <QuizMode config={config} onComplete={onComplete} />;
}


/* =====================================================
   LEARN / NOTES
===================================================== */

function NotesMode({ config, onComplete }) {
  const [notes] = useState(() => getStudyNotes(config.topic));
  const [activeId, setActiveId] = useState(notes[0]?.id);
  const [reviewed, setReviewed] = useState([]);

  if (notes.length === 0) {
    return (
      <div className="practice-empty-state">
        <p>Study notes for {config.topic} will be available soon.</p>
      </div>
    );
  }

  const activeIndex = notes.findIndex((note) => note.id === activeId);
  const note = notes[activeIndex];
  const isReviewed = reviewed.includes(note.id);

  const toggleReviewed = () => {
    setReviewed((current) =>
      current.includes(note.id)
        ? current.filter((id) => id !== note.id)
        : [...current, note.id]
    );
  };

  const finish = () => {
    const reviewedTitles = notes.filter((item) => reviewed.includes(item.id)).map((item) => item.title);
    const remaining = notes.filter((item) => !reviewed.includes(item.id)).map((item) => item.title);

    onComplete({
      title: "Study Session Complete",
      scoreLabel: "Subtopics reviewed",
      score: reviewed.length,
      total: notes.length,
      strongArea: reviewedTitles[0] || "Start reviewing subtopics",
      weakArea: remaining[0] || "All subtopics reviewed",
      note: "Next step: test yourself with a quiz on the same topic.",
      xpActivity: null
    });
  };

  return (
    <div className="tech-notes-layout">

      <aside className="tech-notes-sidebar">

        <div className="tech-notes-tree-root">
          <FiBookOpen />
          <strong>{config.topic}</strong>
        </div>

        <span className="tech-notes-progress">
          {reviewed.length} of {notes.length} reviewed
        </span>

        <ul className="tech-notes-tree">
          {notes.map((item) => (
            <li key={item.id}>
              <button
                type="button"
                className={`tech-notes-link${item.id === activeId ? " active" : ""}`}
                aria-current={item.id === activeId ? "true" : undefined}
                onClick={() => setActiveId(item.id)}
              >
                <span>{item.title}</span>
                {reviewed.includes(item.id) && <FiCheckCircle />}
              </button>
            </li>
          ))}
        </ul>

      </aside>


      <article className="tech-notes-content">

        <div className="tech-notes-header">
          <div>
            <span className="practice-demo-label">Demo learning content</span>
            <h2>{note.title}</h2>
          </div>

          <button
            type="button"
            className={`tech-review-toggle${isReviewed ? " done" : ""}`}
            aria-pressed={isReviewed}
            onClick={toggleReviewed}
          >
            <FiCheck />
            {isReviewed ? "Reviewed" : "Mark as reviewed"}
          </button>
        </div>

        <section className="tech-notes-section concept">
          <h3>Concept</h3>
          <p>{note.concept}</p>
        </section>

        <section className="tech-notes-section">
          <h3>Explanation</h3>
          <p>{note.explanation}</p>
        </section>

        <section className="tech-notes-section">
          <h3>Key Points</h3>
          <ul>
            {note.keyPoints.map((point) => (
              <li key={point}>{point}</li>
            ))}
          </ul>
        </section>

        <section className="tech-notes-section">
          <h3>
            <FiCode />
            Example
          </h3>
          <pre className="practice-code-block"><code>{note.example}</code></pre>
        </section>

        <div className="tech-notes-split">

          <section className="tech-notes-section tips">
            <h3>
              <FiStar />
              Interview Tips
            </h3>
            <ul>
              {note.tips.map((tip) => (
                <li key={tip}>{tip}</li>
              ))}
            </ul>
          </section>

          <section className="tech-notes-section mistakes">
            <h3>
              <FiAlertTriangle />
              Common Mistakes
            </h3>
            <ul>
              {note.mistakes.map((mistake) => (
                <li key={mistake}>{mistake}</li>
              ))}
            </ul>
          </section>

        </div>

        <div className="tech-notes-nav">

          <button
            type="button"
            className="secondary-button"
            onClick={() => setActiveId(notes[activeIndex - 1].id)}
            disabled={activeIndex === 0}
          >
            <FiArrowLeft />
            Previous
          </button>

          {activeIndex < notes.length - 1 ? (
            <button
              type="button"
              className="primary-button"
              onClick={() => setActiveId(notes[activeIndex + 1].id)}
            >
              Next subtopic
              <FiArrowRight />
            </button>
          ) : (
            <button
              type="button"
              className="primary-button"
              onClick={finish}
            >
              Finish Study Session
              <FiArrowRight />
            </button>
          )}

        </div>

      </article>

    </div>
  );
}


/* =====================================================
   QUIZ
===================================================== */

function QuizMode({ config, onComplete }) {
  // AI-generated when available; CareerAI demo questions otherwise (labelled).
  const { status, questions, source, notice } = usePracticeQuestions({
    module: config.module,
    topic: config.topic,
    difficulty: config.difficulty,
    questionType: "mcq",
    count: Number(config.count) || 10
  });

  const handleFinish = ({ correct, total, history }) => {
    onComplete({
      title: "Practice Complete",
      score: correct,
      total,
      ...summarizeAreas(history),
      xpActivity: "TECHNICAL_SET"
    });
  };

  if (status === "loading") {
    return <PracticeLoading label={`Preparing ${config.topic} questions...`} />;
  }

  return (
    <div className="tech-quiz-shell">
      <PracticeSourceNotice source={source} notice={notice} />
      <QuizRunner
        questions={questions}
        heading={`${config.topic} • ${config.difficulty}`}
        showStats
        finishLabel="Finish Quiz"
        onFinish={handleFinish}
      />
    </div>
  );
}


/* =====================================================
   DESCRIPTIVE
===================================================== */

function DescriptiveMode({ config, onComplete }) {
  const { status, questions: prompts, source, notice } = usePracticeQuestions({
    module: config.module,
    topic: config.topic,
    difficulty: config.difficulty,
    questionType: "descriptive",
    count: Number(config.descriptiveCount) || 3
  });
  const [index, setIndex] = useState(0);
  const [answer, setAnswer] = useState("");
  const [feedback, setFeedback] = useState(null);
  const [evaluating, setEvaluating] = useState(false);
  const [results, setResults] = useState([]);

  if (status === "loading") {
    return <PracticeLoading label={`Preparing ${config.topic} questions...`} />;
  }

  if (prompts.length === 0) {
    return (
      <div className="practice-empty-state">
        <p>Descriptive questions for {config.topic} will be available soon.</p>
      </div>
    );
  }

  const prompt = prompts[index];
  const isLast = index === prompts.length - 1;

  const handleSubmit = async (event) => {
    event.preventDefault();
    setEvaluating(true);

    // AI evaluation when available; key-point matching otherwise (labelled).
    const evaluation = await evaluatePracticeAnswer(config, prompt, answer);

    setEvaluating(false);
    setFeedback(evaluation);
    setResults((items) => [...items, evaluation]);
  };

  const handleNext = () => {
    if (isLast) {
      // Each answer is scored as a percentage, so AI and fallback results combine consistently.
      const score = results.reduce((sum, item) => sum + item.percent, 0);
      const total = results.length * 100;
      const allCovered = results.flatMap((item) => item.covered);
      const allMissing = results.flatMap((item) => item.missing);
      const allAi = results.every((item) => item.source === "ai");

      onComplete({
        title: "Practice Complete",
        scoreLabel: "Answer score (out of 100 per question)",
        score,
        total,
        strongArea: allCovered[0] || "Keep practising",
        weakArea: allMissing[0] || "None in this session",
        note: allAi
          ? "Answers were evaluated by the CareerAI AI reviewer. Use the feedback as guidance, not a final grade."
          : "Some answers were scored with demo key-point matching because AI evaluation was unavailable.",
        xpActivity: "TECHNICAL_SET"
      });
      return;
    }

    setIndex((value) => value + 1);
    setAnswer("");
    setFeedback(null);
  };

  return (
    <div className="tech-descriptive">

      <PracticeSourceNotice source={source} notice={notice} />

      <div className="tech-descriptive-header">
        <span className="quiz-runner-heading">
          {config.topic} • {config.difficulty} • Descriptive
        </span>
        <strong>
          Question {index + 1} of {prompts.length}
        </strong>
      </div>

      <div className="tech-descriptive-card">

        <h3>{prompt.question}</h3>

        <form onSubmit={handleSubmit}>

          <label
            className="sr-only"
            htmlFor="descriptive-answer"
          >
            Your answer
          </label>

          <textarea
            id="descriptive-answer"
            className="practice-textarea"
            rows={9}
            placeholder="Write your answer as you would explain it in an interview..."
            value={answer}
            onChange={(event) => setAnswer(event.target.value)}
            disabled={Boolean(feedback)}
          />

          <div className="tech-descriptive-meta">
            <span>{answer.trim().split(/\s+/).filter(Boolean).length} words</span>

            {!feedback && (
              <button
                type="submit"
                className="primary-button"
                disabled={answer.trim().length < 20 || evaluating}
              >
                <FiSend />
                {evaluating ? "Evaluating..." : "Submit Answer"}
              </button>
            )}
          </div>

        </form>

      </div>


      {feedback && (
        <div
          className="tech-feedback"
          aria-live="polite"
        >

          <div className="tech-feedback-head">
            <span className="practice-demo-label">
              {feedback.source === "ai" ? "AI feedback" : "Demo feedback · key-point matching"}
            </span>
            <strong>
              {feedback.scoreText}
            </strong>
          </div>

          {feedback.notice && (
            <p className="tech-feedback-note">{feedback.notice}</p>
          )}

          {feedback.feedbackText && (
            <p className="tech-feedback-note">{feedback.feedbackText}</p>
          )}

          {feedback.lengthNote && (
            <p className="tech-feedback-note">{feedback.lengthNote}</p>
          )}

          <div className="tech-feedback-grid">

            <div className="tech-feedback-box good">
              <h4>
                <FiCheckCircle />
                Good points
              </h4>
              {feedback.covered.length > 0 ? (
                <ul>
                  {feedback.covered.map((item) => (
                    <li key={item}>{item}</li>
                  ))}
                </ul>
              ) : (
                <p>No key points detected yet.</p>
              )}
            </div>

            <div className="tech-feedback-box missing">
              <h4>
                <FiXCircle />
                Missing
              </h4>
              {feedback.missing.length > 0 ? (
                <ul>
                  {feedback.missing.map((item) => (
                    <li key={item}>{item}</li>
                  ))}
                </ul>
              ) : (
                <p>You covered every key point.</p>
              )}
            </div>

          </div>

          <div className="tech-feedback-box example">
            <h4>
              <FiStar />
              {feedback.source === "ai" ? "Suggested answer" : "Example stronger answer"}
            </h4>
            <p>{feedback.strongerAnswer}</p>
          </div>

          <div className="quiz-actions">
            <button
              type="button"
              className="primary-button"
              onClick={handleNext}
            >
              {isLast ? "Finish Practice" : "Next Question"}
              <FiArrowRight />
            </button>
          </div>

        </div>
      )}

    </div>
  );
}

export default TechnicalWorkspace;
