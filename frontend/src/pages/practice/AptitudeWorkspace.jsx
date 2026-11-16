import { FiInfo } from "react-icons/fi";
import QuizRunner from "./QuizRunner";
import { usePracticeQuestions } from "./usePracticeQuestions";
import { PracticeLoading, PracticeSourceNotice } from "./PracticeSourceNotice";
import { formatClock, summarizeAreas } from "./practiceUtils";

function AptitudeWorkspace({ config, onComplete }) {
  const requested = Number(config.count) || 10;

  // AI-generated when available; CareerAI demo questions otherwise (labelled).
  const { status, questions, source, notice } = usePracticeQuestions({
    module: config.module,
    topic: config.topic,
    difficulty: config.difficulty,
    questionType: "mcq",
    count: requested
  });

  const handleFinish = ({ correct, total, history, seconds }) => {
    onComplete({
      title: "Practice Complete",
      score: correct,
      total,
      stats: [{ label: "Time Taken", value: formatClock(seconds) }],
      ...summarizeAreas(history),
      xpActivity: "APTITUDE_SET"
    });
  };

  if (status === "loading") {
    return <PracticeLoading label={`Preparing ${config.topic} questions...`} />;
  }

  return (
    <div className="aptitude-shell">

      <PracticeSourceNotice source={source} notice={notice} />

      {questions.length < requested && (
        <p className="practice-inline-note">
          <FiInfo />
          {source === "ai"
            ? `${questions.length} usable questions were generated, so this set is shorter than ${requested}.`
            : `The demo bank has ${questions.length} ${config.topic} questions, so this set is shorter than ${requested}.`}
        </p>
      )}

      <QuizRunner
        questions={questions}
        heading={`${config.topic} • ${config.difficulty}`}
        showStats
        showTimer
        finishLabel="Finish Practice"
        onFinish={handleFinish}
      />

    </div>
  );
}

export default AptitudeWorkspace;
