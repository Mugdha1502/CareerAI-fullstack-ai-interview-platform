import { useState } from "react";
import {
  FiMic,
  FiInfo,
  FiEdit3,
  FiSend,
  FiArrowRight,
  FiCheckCircle,
  FiAlertCircle
} from "react-icons/fi";
import { getVoicePrompts } from "./practiceProvider";
import { evaluateVoiceAnswer } from "./practiceEvaluators";

const toneOf = (value) =>
  value === "Good" ? "good" : value === "Fair" ? "fair" : "weak";

function VoiceWorkspace({ config, onComplete }) {
  const [topicData] = useState(() => getVoicePrompts(config.topic));
  const [index, setIndex] = useState(0);
  const [showVoiceNotice, setShowVoiceNotice] = useState(false);
  const [isTyping, setIsTyping] = useState(false);
  const [answer, setAnswer] = useState("");
  const [feedback, setFeedback] = useState(null);
  const [results, setResults] = useState([]);

  const question = topicData.questions[index];
  const isLast = index === topicData.questions.length - 1;

  const handleRecord = () => {
    // TODO voice: start a realtime voice session (Vapi) via the backend.
    setShowVoiceNotice(true);
    setIsTyping(true);
  };

  const handleSubmit = (event) => {
    event.preventDefault();

    const evaluation = evaluateVoiceAnswer(topicData, answer);

    setFeedback(evaluation);
    setResults((items) => [...items, evaluation]);
  };

  const handleNext = () => {
    if (isLast) {
      const score = results.reduce((sum, item) => sum + item.score, 0);
      const total = results.reduce((sum, item) => sum + item.total, 0);
      const criteria = ["structure", "clarity", "relevance"];
      const averages = criteria.map((key) => ({
        key,
        good: results.filter((item) => item[key] === "Good").length
      }));
      const best = [...averages].sort((a, b) => b.good - a.good)[0];
      const worst = [...averages].sort((a, b) => a.good - b.good)[0];
      const label = (key) => key.charAt(0).toUpperCase() + key.slice(1);

      onComplete({
        title: "Practice Complete",
        scoreLabel: "Criteria rated Good",
        score,
        total,
        strongArea: best.good > 0 ? label(best.key) : "Keep practising",
        weakArea: worst.good < results.length ? label(worst.key) : "None in this session",
        note: "Demo feedback on your typed answers. Voice analysis will be connected with the real-time voice service later.",
        xpActivity: null
      });
      return;
    }

    setIndex((value) => value + 1);
    setAnswer("");
    setFeedback(null);
  };

  return (
    <div className="voice-workspace">

      <div className="voice-stage">

        <span className="quiz-runner-heading">
          {config.topic} • Question {index + 1} of {topicData.questions.length}
        </span>

        <h2>{question}</h2>

        <div
          className="voice-mic"
          aria-hidden="true"
        >
          <span className="voice-mic-ring"></span>
          <span className="voice-mic-ring delay"></span>
          <div className="voice-mic-core">
            <FiMic />
          </div>
        </div>

        <button
          type="button"
          className="voice-record-button"
          onClick={handleRecord}
        >
          <FiMic />
          Start Recording
        </button>

        {showVoiceNotice && (
          <p
            className="voice-notice"
            role="status"
          >
            <FiInfo />
            Voice recording will be connected with the real-time voice service later. Your microphone has not been accessed — type your answer below instead.
          </p>
        )}

        {!isTyping && (
          <button
            type="button"
            className="voice-type-link"
            onClick={() => setIsTyping(true)}
          >
            <FiEdit3 />
            Type your answer instead
          </button>
        )}

        <ul className="voice-tips">
          {topicData.tips.map((tip) => (
            <li key={tip}>{tip}</li>
          ))}
        </ul>

      </div>


      {isTyping && (
        <form
          className="voice-answer"
          onSubmit={handleSubmit}
        >

          <label htmlFor="voice-answer">
            Your answer
          </label>

          <textarea
            id="voice-answer"
            className="practice-textarea"
            rows={7}
            placeholder="Write your answer the way you would say it aloud..."
            value={answer}
            onChange={(event) => setAnswer(event.target.value)}
            disabled={Boolean(feedback)}
          />

          {!feedback && (
            <div className="tech-descriptive-meta">
              <span>{answer.trim().split(/\s+/).filter(Boolean).length} words</span>
              <button
                type="submit"
                className="primary-button"
                disabled={answer.trim().length < 20}
              >
                <FiSend />
                Submit Answer
              </button>
            </div>
          )}

        </form>
      )}


      {feedback && (
        <section
          className="voice-feedback"
          aria-live="polite"
        >

          <span className="practice-demo-label">Demo feedback</span>

          <div className="voice-feedback-grid">
            {[
              ["Structure", feedback.structure],
              ["Clarity", feedback.clarity],
              ["Relevance", feedback.relevance]
            ].map(([label, value]) => (
              <div
                key={label}
                className={`voice-score ${toneOf(value)}`}
              >
                {value === "Good" ? <FiCheckCircle /> : <FiAlertCircle />}
                <span>{label}</span>
                <strong>{value}</strong>
              </div>
            ))}
          </div>

          <div className="tech-feedback-box example">
            <h4>Suggested improvement</h4>
            <ul>
              {feedback.suggestions.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
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

        </section>
      )}

    </div>
  );
}

export default VoiceWorkspace;
