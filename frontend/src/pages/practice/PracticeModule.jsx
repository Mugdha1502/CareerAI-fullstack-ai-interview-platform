import { useRef, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import {
  FiBookOpen,
  FiCode,
  FiDatabase,
  FiGrid,
  FiMic,
  FiTarget,
  FiClock,
  FiZap,
  FiArrowLeft,
  FiCheckCircle,
  FiEdit3,
  FiHelpCircle,
  FiFileText,
  FiSettings
} from "react-icons/fi";
import { XP_REWARDS } from "../../services/xpService";
import { useAuth } from "../../context/AuthContext";
import { getSettings } from "../../services/settingsService";
import {
  canSavePractice,
  startPracticeSession,
  savePracticeResult,
  completePracticeSession
} from "../../services/practiceService";
import PracticeSetup from "./PracticeSetup";
import PracticeResult from "./PracticeResult";
import PracticeSaveStatus from "./PracticeSaveStatus";
import TechnicalWorkspace from "./TechnicalWorkspace";
import AptitudeWorkspace from "./AptitudeWorkspace";
import CodingWorkspace from "./CodingWorkspace";
import SQLWorkspace from "./SQLWorkspace";
import SystemDesignWorkspace from "./SystemDesignWorkspace";
import VoiceWorkspace from "./VoiceWorkspace";
import DailyChallengeWorkspace from "./DailyChallengeWorkspace";
import MockAssessmentWorkspace from "./MockAssessmentWorkspace";
import { technicalTopics } from "./technicalMockData";
import {
  aptitudeTopics,
  aptitudeQuestionCounts,
  codingTopics,
  codingLanguages,
  sqlTopics,
  sqlDatasets,
  systemDesignChallenges,
  voiceTopics
} from "./practiceMockData";

const difficultyField = {
  key: "difficulty",
  label: "Difficulty",
  options: ["Beginner", "Intermediate", "Advanced"],
  required: true,
  defaultValue: "Beginner",
  layout: "segmented"
};

const topicField = (options, label = "Topic") => ({
  key: "topic",
  label,
  options,
  required: true,
  missingMessage: `Select ${label === "Topic" ? "a topic" : `an ${label.toLowerCase()}`} to continue`
});

// AI generation supports up to 20 MCQs and 5 descriptive questions per set.
const technicalQuizCounts = ["5", "10", "15"];
const technicalDescriptiveCounts = ["3", "5"];

const practiceTypeLabels = {
  notes: "Learn / Notes",
  quiz: "Quiz",
  descriptive: "Descriptive"
};

const modules = {
  technical: {
    icon: <FiBookOpen />,
    title: "Technical Practice",
    subtitle: "Prepare programming languages, frameworks and core computer science subjects.",
    workspace: TechnicalWorkspace,
    fields: [
      difficultyField,
      topicField(technicalTopics),
      {
        key: "practiceType",
        label: "Practice Type",
        required: true,
        layout: "cards",
        showWhen: (values) => Boolean(values.topic),
        missingMessage: "Select a practice type to continue",
        options: [
          { value: "notes", label: "Learn / Notes", description: "Structured notes, examples and interview tips", icon: <FiBookOpen /> },
          { value: "quiz", label: "Quiz", description: "Multiple-choice questions with explanations", icon: <FiHelpCircle /> },
          { value: "descriptive", label: "Descriptive", description: "Write interview answers and get feedback", icon: <FiEdit3 /> }
        ]
      },
      {
        key: "count",
        label: "Number of Questions",
        options: technicalQuizCounts.map((value) => ({ value, label: `${value} questions` })),
        required: true,
        defaultValue: "10",
        layout: "segmented",
        showWhen: (values) => values.practiceType === "quiz"
      },
      {
        key: "descriptiveCount",
        label: "Number of Questions",
        options: technicalDescriptiveCounts.map((value) => ({ value, label: `${value} questions` })),
        required: true,
        defaultValue: "3",
        layout: "segmented",
        showWhen: (values) => values.practiceType === "descriptive"
      }
    ],
    highlights: ["Study notes with examples", "Quizzes with explanations", "Descriptive answers with feedback"],
    examples: [
      "What is the difference between an interface and an abstract class?",
      "Explain dependency injection in Spring Boot.",
      "What is normalization in DBMS?",
      "What is the difference between TCP and UDP?"
    ]
  },
  aptitude: {
    icon: <FiGrid />,
    title: "Aptitude Practice",
    subtitle: "Prepare quantitative aptitude, logical reasoning, verbal ability and data interpretation.",
    workspace: AptitudeWorkspace,
    fields: [
      difficultyField,
      topicField(aptitudeTopics),
      {
        key: "count",
        label: "Number of Questions",
        options: aptitudeQuestionCounts.map((value) => ({ value, label: `${value} questions` })),
        required: true,
        defaultValue: "10",
        layout: "segmented"
      }
    ],
    highlights: ["Timed multiple-choice practice", "Step-by-step explanations", "Accuracy tracking"],
    examples: [
      "A price increases by 20% and then decreases by 20%. What is the final price?",
      "Find the next number in the series 2, 6, 12, 20, 30, ?",
      "Choose the grammatically correct sentence.",
      "Interpret the values shown in a data table."
    ]
  },
  coding: {
    icon: <FiCode />,
    title: "Coding Practice",
    subtitle: "Solve coding problems across data structures and algorithms.",
    workspace: CodingWorkspace,
    fields: [
      difficultyField,
      topicField(codingTopics),
      {
        key: "language",
        label: "Language",
        options: codingLanguages,
        required: true,
        defaultValue: "Java",
        layout: "segmented"
      }
    ],
    highlights: ["Problem statement, examples and constraints", "Code editor in four languages", "Secure sandboxed execution with hidden tests"],
    examples: [
      "Find the second largest element in an array.",
      "Check whether a string is a palindrome.",
      "Find the first non-repeating character.",
      "Implement binary search."
    ]
  },
  sql: {
    icon: <FiDatabase />,
    title: "SQL Practice",
    subtitle: "Write SQL queries against controlled practice datasets.",
    workspace: SQLWorkspace,
    fields: [
      difficultyField,
      topicField(sqlTopics),
      {
        key: "dataset",
        label: "Practice Dataset",
        required: true,
        defaultValue: "HR Company",
        layout: "cards",
        options: Object.entries(sqlDatasets).map(([name, dataset]) => ({
          value: name,
          label: name,
          description: dataset.tables.map((table) => table.name).join(" · "),
          icon: <FiDatabase />
        }))
      }
    ],
    highlights: ["Schema preview with sample rows", "Read-only SQL in an isolated practice database", "Real result tables and answer checking"],
    examples: [
      "Find the second highest salary.",
      "List departments with more than one employee.",
      "Rank employees within each department.",
      "Find products priced above the average price."
    ]
  },
  "system-design": {
    icon: <FiTarget />,
    title: "System Design Practice",
    subtitle: "Learn how to structure system-design interview answers.",
    workspace: SystemDesignWorkspace,
    fields: [
      difficultyField,
      topicField(Object.keys(systemDesignChallenges), "Topic")
    ],
    highlights: ["Eight-part design workspace", "Hints for every section", "Rubric-based review (demo)"],
    examples: [
      "Design a URL shortener.",
      "Design a chat application.",
      "Design an e-commerce platform.",
      "Design a rate limiter."
    ]
  },
  voice: {
    icon: <FiMic />,
    title: "Voice Practice",
    subtitle: "Practise speaking technical, HR and behavioural answers aloud.",
    workspace: VoiceWorkspace,
    fields: [
      difficultyField,
      topicField(Object.keys(voiceTopics))
    ],
    highlights: ["Interview-style speaking prompts", "Typed-answer fallback", "Structure, clarity and relevance feedback"],
    examples: [
      "Tell me about yourself.",
      "Explain your final-year project.",
      "Describe a challenge you faced.",
      "Why should we hire you?"
    ]
  },
  "daily-challenge": {
    icon: <FiZap />,
    title: "Daily Challenge",
    subtitle: "Complete one short mixed challenge each day and maintain your streak.",
    workspace: null
  },
  "mock-assessment": {
    icon: <FiClock />,
    title: "Mock Assessments",
    subtitle: "Take timed recruitment-style tests and review your final score.",
    workspace: MockAssessmentWorkspace,
    startLabel: "Start Assessment",
    fields: [
      {
        ...topicField(
          [
            "Java Assessment",
            "Frontend Assessment",
            "Backend Assessment",
            "SQL Assessment",
            "Aptitude Test",
            "Mixed Assessment"
          ].map((value) => ({ value, label: value, icon: <FiFileText /> })),
          "Assessment Type"
        ),
        layout: "cards"
      },
      difficultyField,
      {
        key: "count",
        label: "Questions",
        options: ["10", "20", "30"].map((value) => ({ value, label: `${value} questions` })),
        required: true,
        defaultValue: "10",
        layout: "segmented"
      },
      {
        key: "duration",
        label: "Duration",
        options: ["15", "30", "45"].map((value) => ({ value, label: `${value} minutes` })),
        required: true,
        defaultValue: "15",
        layout: "segmented"
      }
    ],
    highlights: ["Timed recruitment-style paper", "Question navigator and flags", "Score with section breakdown"],
    examples: [
      "30-question Java assessment",
      "Frontend developer mock test",
      "SQL technical assessment",
      "Mixed placement assessment"
    ]
  }
};

function questionCountLabel(config) {
  if (config.practiceType === "notes") return null;
  const count = config.practiceType === "descriptive" ? config.descriptiveCount : config.count;
  return count ? `${count} questions` : null;
}

function configChips(config) {
  return [
    config.topic,
    config.difficulty,
    practiceTypeLabels[config.practiceType],
    config.language,
    config.dataset,
    questionCountLabel(config),
    config.duration && `${config.duration} minutes`
  ].filter(Boolean);
}


// Option values for a setup field, or [] when the module has no such field.
// Special modules (Daily Challenge) have no setup fields at all.
function getFieldOptionValues(fields, key) {
  if (!Array.isArray(fields)) return [];

  const field = fields.find((item) => item.key === key);

  if (!field || !Array.isArray(field.options)) return [];

  return field.options.map((option) => (typeof option === "string" ? option : option.value));
}


function PracticeNotFound({ title = "Practice module not found", message }) {
  return (
    <div className="tool-page">
      <div className="section-container">
        <h1>{title}</h1>
        {message && <p>{message}</p>}
        <Link
          to="/practice"
          className="primary-button"
        >
          Return to Practice
        </Link>
      </div>
    </div>
  );
}


function PracticeModule() {
  const { module } = useParams();
  const selected = modules[module];

  if (!selected) {
    return <PracticeNotFound />;
  }

  // Daily Challenge is self-contained: no topic setup, its own quiz, result and XP.
  if (module === "daily-challenge") {
    return (
      <PracticeShell
        module={module}
        selected={selected}
      >
        <DailyChallengeWorkspace />
      </PracticeShell>
    );
  }

  // Every other module (including Mock Assessment, whose setup chooses the
  // assessment type, length and duration) needs setup fields and a workspace.
  if (!Array.isArray(selected.fields) || !selected.workspace) {
    return (
      <PracticeNotFound
        title={`${selected.title} is unavailable`}
        message="This practice module is not configured yet."
      />
    );
  }

  // key resets all session state when switching between modules.
  return (
    <PracticeModuleView
      key={module}
      module={module}
      selected={selected}
    />
  );
}


function PracticeShell({ module, selected, children }) {
  return (
    <div className={`practice-detail-page practice-theme-${module}`}>

      <section className="practice-detail-hero">

        <div className="section-container">

          <Link
            to="/practice"
            className="page-back-link"
          >
            <FiArrowLeft />
            All Practice Modules
          </Link>

          <div className="practice-detail-heading">

            <div className="practice-detail-large-icon">
              {selected.icon}
            </div>

            <div>
              <span className="section-tag">
                CAREERAI PRACTICE
              </span>
              <h1>{selected.title}</h1>
              <p>{selected.subtitle}</p>
            </div>

          </div>

        </div>

      </section>


      <section className="practice-module-body">

        <div className="section-container">
          {children}
        </div>

      </section>

    </div>
  );
}


function PracticeModuleView({ module, selected }) {
  const [searchParams] = useSearchParams();
  const { user } = useAuth();

  // Settings → Preparation Preferences → Default difficulty.
  const defaultDifficulty = getSettings(user?.id).defaultDifficulty;

  // ?topic= deep links (Career Tracks, Skill Gap) preselect a topic only if this
  // module offers it; an unknown topic simply leaves the selection empty.
  const requestedTopic = searchParams.get("topic") || "";
  const topicOptions = getFieldOptionValues(selected.fields, "topic");
  const initialTopic = topicOptions.includes(requestedTopic) ? requestedTopic : "";

  // ?type=notes|quiz|descriptive preselects the practice type where the module has one.
  const requestedType = searchParams.get("type") || "";
  const typeOptions = getFieldOptionValues(selected.fields, "practiceType");
  const initialType = initialTopic && typeOptions.includes(requestedType) ? requestedType : "";

  const [stage, setStage] = useState("setup");
  const [config, setConfig] = useState(null);
  const [sessionId, setSessionId] = useState(0);
  const [outcome, setOutcome] = useState(null);

  // Backend persistence: the session is created when practice starts and the
  // result is saved when it finishes. null | "saving" | "saved" | "failed" | "guest".
  const [saveStatus, setSaveStatus] = useState(null);
  const sessionCounter = useRef(0);
  const remoteSession = useRef(null);

  const Workspace = selected.workspace;

  const startSession = (values) => {
    const localId = sessionCounter.current + 1;
    sessionCounter.current = localId;

    // Created in the background so practice never waits on the network.
    remoteSession.current = canSavePractice()
      ? {
          localId,
          request: startPracticeSession({
            module,
            topic: values.topic,
            difficulty: values.difficulty,
            questionType: values.practiceType || values.language || values.dataset
          }).catch(() => null)
        }
      : null;

    setConfig({ ...values, module });
    setSessionId(localId);
    setOutcome(null);
    setSaveStatus(null);
    setStage("workspace");
  };

  // Coding/SQL submissions attach their scored result to this session on the server.
  const getRemoteSession = () => remoteSession.current?.request || Promise.resolve(null);

  const persistResult = (result) => {
    const remote = remoteSession.current;
    remoteSession.current = null; // a session is saved at most once

    // Already scored and saved by the server (coding / SQL submissions): nothing more to send.
    if (result.savedByServer) {
      setSaveStatus("saved");
      return;
    }

    if (!remote) {
      setSaveStatus(canSavePractice() ? null : "guest");
      return;
    }

    setSaveStatus("saving");

    remote.request
      .then((session) => {
        if (!session) throw new Error("Session was not created");

        return result.total > 0
          ? savePracticeResult({ sessionId: session.id, score: result.score, totalQuestions: result.total })
          : completePracticeSession(session.id);
      })
      .then(() => setSaveStatus("saved"))
      .catch(() => setSaveStatus("failed"));
  };

  const handleComplete = (result) => {
    const amount = result.xpActivity ? XP_REWARDS[result.xpActivity] : 0;

    // Regular practice only displays the reward; nothing is written to xpService,
    // so repeated sessions cannot farm XP. Daily Challenge awards its own XP.
    // TODO backend: award XP server-side when sessions are submitted to the API.
    const xp = amount
      ? { amount, status: "pending" }
      : { amount: 0, status: "none" };

    setOutcome({ result, xp });
    setStage("result");
    persistResult(result);
  };

  const leaveSession = () => {
    if (stage === "workspace" && !window.confirm("Leave this session and change setup? Your current progress will be lost.")) {
      return;
    }

    // An abandoned session stays IN_PROGRESS on the backend with no result.
    remoteSession.current = null;

    setStage("setup");
    setOutcome(null);
    setSaveStatus(null);
  };

  return (
    <PracticeShell
      module={module}
      selected={selected}
    >

      {stage === "setup" && (
        <div className="practice-detail-layout">

          <PracticeSetup
            key={`${initialTopic}|${initialType}|${defaultDifficulty}`}
            fields={selected.fields}
            initialTopic={initialTopic}
            initialValues={{
              difficulty: defaultDifficulty,
              ...(initialType ? { practiceType: initialType } : {})
            }}
            startLabel={selected.startLabel || "Start Practice"}
            onStart={startSession}
          />

          <aside className="practice-detail-sidebar">

            {Array.isArray(selected.highlights) && selected.highlights.length > 0 && (
              <div className="practice-side-card">
                <span>WHAT YOU WILL PRACTISE</span>
                <ul className="practice-side-list">
                  {selected.highlights.map((item) => (
                    <li key={item}>
                      <FiCheckCircle />
                      {item}
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {Array.isArray(selected.examples) && selected.examples.length > 0 && (
              <div className="practice-side-card">
                <span>EXAMPLE QUESTIONS</span>
                <ol className="practice-example-list">
                  {selected.examples.map((example) => (
                    <li key={example}>{example}</li>
                  ))}
                </ol>
              </div>
            )}

          </aside>

        </div>
      )}


      {stage === "workspace" && config && (
        <div className="practice-workspace">

          <div className="practice-workspace-bar">

            <div className="practice-workspace-chips">
              {configChips(config).map((chip) => (
                <span key={chip}>{chip}</span>
              ))}
            </div>

            <button
              type="button"
              className="practice-change-setup"
              onClick={leaveSession}
            >
              <FiSettings />
              Change setup
            </button>

          </div>

          <Workspace
            key={sessionId}
            config={config}
            onComplete={handleComplete}
            getRemoteSession={getRemoteSession}
          />

        </div>
      )}


      {stage === "result" && outcome && (
        <PracticeResult
          result={outcome.result}
          xp={outcome.xp}
          retryLabel={module === "mock-assessment" ? "Retry" : "Practice Again"}
          onRetry={() => startSession(config)}
          extra={<PracticeSaveStatus status={saveStatus} />}
        />
      )}

    </PracticeShell>
  );
}

export default PracticeModule;
