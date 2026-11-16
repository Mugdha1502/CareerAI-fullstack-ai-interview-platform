import { useEffect, useState } from "react";
import {
  FiPlay,
  FiSend,
  FiRotateCcw,
  FiCheckCircle,
  FiXCircle,
  FiShield,
  FiTerminal,
  FiLoader,
  FiAlertCircle,
  FiArrowRight
} from "react-icons/fi";
import { getCodingProblem } from "./practiceProvider";
import { codingLanguages, buildStarterCode } from "./practiceMockData";
import {
  CODE_LANGUAGE_IDS,
  EXECUTION_UNAVAILABLE_MESSAGE,
  canSavePractice,
  executeCode,
  executionError,
  getCodingQuestion,
  submitCode
} from "../../services/practiceService";

/* Code is only ever executed by the backend's external sandbox (JDoodle / Judge0). Run Code uses the
   custom input and is never saved; Submit Solution is evaluated against hidden tests on the
   server and saved as the practice result. */

const RUN_STATUS_LABELS = {
  ACCEPTED: "Accepted",
  COMPILATION_ERROR: "Compilation Error",
  RUNTIME_ERROR: "Runtime Error",
  TIME_LIMIT_EXCEEDED: "Time Limit Exceeded",
  MEMORY_LIMIT_EXCEEDED: "Memory Limit Exceeded",
  INTERNAL_ERROR: "Execution error"
};

const SUBMIT_STATUS_LABELS = {
  PASSED: "All tests passed",
  PARTIAL: "Some tests passed",
  FAILED: "Wrong answer",
  COMPILATION_ERROR: "Compilation Error",
  RUNTIME_ERROR: "Runtime Error",
  TIME_LIMIT_EXCEEDED: "Time Limit Exceeded"
};

const TEST_STATUS_LABELS = {
  PASSED: "Passed",
  WRONG_ANSWER: "Wrong answer",
  COMPILATION_ERROR: "Compilation error",
  RUNTIME_ERROR: "Runtime error",
  TIME_LIMIT_EXCEEDED: "Time limit exceeded",
  MEMORY_LIMIT_EXCEEDED: "Memory limit exceeded",
  INTERNAL_ERROR: "Not evaluated"
};

function formatMemory(kb) {
  if (kb == null) return "–";
  return kb >= 1024 ? `${(kb / 1024).toFixed(1)} MB` : `${kb} KB`;
}

function CodingWorkspace({ config, onComplete, getRemoteSession }) {
  const signedIn = canSavePractice();
  const [localProblem] = useState(() => getCodingProblem(config.topic));
  const [question, setQuestion] = useState(null);
  const [loadState, setLoadState] = useState(signedIn ? "loading" : "guest"); // loading | ready | guest | error
  const [language, setLanguage] = useState(config.language || "Java");
  const [code, setCode] = useState(() => buildStarterCode(localProblem, config.language || "Java"));
  const [stdin, setStdin] = useState("");
  const [running, setRunning] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [run, setRun] = useState(null);
  const [submission, setSubmission] = useState(null);
  const [message, setMessage] = useState("");

  // Signed-in users practise the server's curated question (its starter code matches the
  // server's test harness). Guests see the local problem without execution.
  useEffect(() => {
    if (!signedIn) return undefined;
    let cancelled = false;
    getCodingQuestion(config.topic)
      .then((data) => {
        if (cancelled) return;
        setQuestion(data);
        setCode(data.starterCode[CODE_LANGUAGE_IDS[config.language || "Java"]] || "");
        setStdin(data.examples[0]?.input || "");
        setLoadState("ready");
      })
      .catch((error) => {
        if (cancelled) return;
        setMessage(executionError(error).message);
        setLoadState("error");
      });
    return () => {
      cancelled = true;
    };
  }, [signedIn, config.topic, config.language]);

  const starterFor = (lang) => (question ? question.starterCode[CODE_LANGUAGE_IDS[lang]] || "" : buildStarterCode(localProblem, lang));
  const starter = starterFor(language);
  const lineCount = code.split("\n").length;
  const executionAvailable = Boolean(question?.executionAvailable);
  const canExecute = loadState === "ready" && executionAvailable && !running && !submitting && !submission;

  const changeLanguage = (next) => {
    if (next === language) return;
    const hasEdits = code.trim() !== starter.trim();
    if (hasEdits && !window.confirm("Switching language will replace your current code with new starter code. Continue?")) {
      return;
    }
    setLanguage(next);
    setCode(starterFor(next));
    setRun(null);
    setMessage("");
  };

  const handleReset = () => {
    if (code.trim() !== starter.trim() && !window.confirm("Reset your code to the starter template?")) {
      return;
    }
    setCode(starter);
    setRun(null);
    setMessage("");
  };

  // Tab inserts spaces instead of moving focus out of the editor.
  const handleKeyDown = (event) => {
    if (event.key !== "Tab" || event.shiftKey) return;
    event.preventDefault();
    const { selectionStart, selectionEnd } = event.target;
    const next = `${code.slice(0, selectionStart)}    ${code.slice(selectionEnd)}`;
    setCode(next);
    requestAnimationFrame(() => {
      event.target.selectionStart = selectionStart + 4;
      event.target.selectionEnd = selectionStart + 4;
    });
  };

  const handleRun = async () => {
    if (!canExecute) return;
    setRunning(true);
    setMessage("");
    setRun(null);
    try {
      setRun(await executeCode({ language: CODE_LANGUAGE_IDS[language], sourceCode: code, stdin, questionId: question.id }));
    } catch (error) {
      setMessage(executionError(error).message);
    } finally {
      setRunning(false);
    }
  };

  const handleSubmit = async () => {
    if (!canExecute) return;
    if (code.trim() === starter.trim()) {
      setMessage("Write a solution before submitting.");
      return;
    }
    setSubmitting(true);
    setMessage("");
    try {
      const session = getRemoteSession ? await getRemoteSession() : null;
      setSubmission(await submitCode({ questionId: question.id, language: CODE_LANGUAGE_IDS[language], sourceCode: code, sessionId: session?.id }));
    } catch (error) {
      setMessage(executionError(error).message);
    } finally {
      setSubmitting(false);
    }
  };

  const finish = () => {
    onComplete({
      title: "Practice Complete",
      scoreLabel: "Hidden tests passed",
      score: submission.passedTests,
      total: submission.totalTests,
      strongArea: config.topic,
      weakArea: submission.passedTests < submission.totalTests ? "Edge cases" : "None in this session",
      note: `Passed ${submission.passedTests} / ${submission.totalTests} hidden tests · Score ${submission.score}%`,
      xpActivity: "CODING_PROBLEM",
      savedByServer: true
    });
  };

  const view = question
    ? {
      title: question.title,
      description: question.description,
      examples: question.examples.map((example) => ({ input: example.input, output: example.output, explanation: example.explanation })),
      constraints: question.constraints,
      inputFormat: question.inputFormat,
      outputFormat: question.outputFormat
    }
    : localProblem;

  return (
    <div className="coding-layout">

      {/* PROBLEM */}

      <section className="coding-problem">

        <div className="coding-problem-head">
          <span className={`coding-difficulty ${config.difficulty.toLowerCase()}`}>
            {config.difficulty}
          </span>
          <span className="coding-topic">{config.topic}</span>
        </div>

        <h2>{view.title}</h2>

        <p>{view.description}</p>

        {view.inputFormat && (
          <>
            <h3>Input / Output</h3>
            <p className="coding-io-format"><strong>Input:</strong> {view.inputFormat}</p>
            <p className="coding-io-format"><strong>Output:</strong> {view.outputFormat}</p>
          </>
        )}

        <h3>Examples</h3>

        {view.examples.map((example, index) => (
          <div key={`${index}-${example.input}`} className="coding-example">
            <strong>Example {index + 1}</strong>
            <code>Input: {example.input === "" ? "(empty)" : example.input}</code>
            <code>Output: {example.output === "" ? "(empty)" : example.output}</code>
            {example.explanation && <small>{example.explanation}</small>}
          </div>
        ))}

        <h3>Constraints</h3>

        <ul className="coding-constraints">
          {view.constraints.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>

      </section>


      {/* EDITOR */}

      <section className="coding-editor-panel">

        <div className="coding-editor-toolbar">

          <label className="coding-language">
            <span className="sr-only">Language</span>
            <select value={language} onChange={(event) => changeLanguage(event.target.value)} disabled={running || submitting}>
              {codingLanguages.map((item) => (
                <option key={item} value={item}>{item}</option>
              ))}
            </select>
          </label>

          <span className="coding-demo-pill">
            <FiShield />
            {question ? "Secure sandbox" : "Preview"}
          </span>

          <button type="button" className="coding-tool-button" onClick={handleReset} disabled={running || submitting}>
            <FiRotateCcw />
            Reset
          </button>

        </div>

        {loadState === "loading" && (
          <p className="coding-console-warning coding-banner"><FiLoader className="resume-spin" /> Loading the problem…</p>
        )}
        {loadState === "guest" && (
          <p className="coding-console-warning coding-banner">Sign in to run and submit your code. Code runs in a secure sandbox on the server.</p>
        )}
        {loadState === "error" && (
          <p className="coding-console-warning coding-banner"><FiAlertCircle /> {message || "The problem could not be loaded."}</p>
        )}
        {loadState === "ready" && !executionAvailable && (
          <p className="coding-console-warning coding-banner"><FiAlertCircle /> {EXECUTION_UNAVAILABLE_MESSAGE}. You can still write your solution.</p>
        )}

        <div className="coding-editor">

          <div className="coding-gutter" aria-hidden="true">
            {Array.from({ length: lineCount }, (_, line) => (
              <span key={line}>{line + 1}</span>
            ))}
          </div>

          <textarea
            aria-label={`${language} code editor`}
            spellCheck={false}
            wrap="off"
            rows={Math.max(lineCount + 1, 16)}
            value={code}
            onChange={(event) => setCode(event.target.value)}
            onKeyDown={handleKeyDown}
            readOnly={Boolean(submission)}
          />

        </div>

        <label className="coding-stdin">
          <span>Custom input (stdin)</span>
          <textarea
            aria-label="Custom input"
            spellCheck={false}
            rows={3}
            value={stdin}
            onChange={(event) => setStdin(event.target.value)}
            disabled={!question}
          />
        </label>


        <div className="coding-console" aria-live="polite">

          <div className="coding-console-head">
            <FiTerminal />
            <strong>{submission ? "Submission" : "Output"}</strong>
            {run && !submission && (
              <span className={run.status === "ACCEPTED" ? "pass" : "fail"}>
                {RUN_STATUS_LABELS[run.status] || run.status}
              </span>
            )}
            {submission && (
              <span className={submission.status === "PASSED" ? "pass" : "fail"}>
                {SUBMIT_STATUS_LABELS[submission.status] || submission.status}
              </span>
            )}
          </div>

          {(running || submitting) && (
            <p className="coding-console-empty"><FiLoader className="resume-spin" /> {submitting ? "Running hidden tests…" : "Running…"}</p>
          )}

          {!run && !submission && !running && !submitting && (
            <p className="coding-console-empty">Run your code with the custom input, then submit to check it against hidden tests.</p>
          )}

          {run && !submission && (
            <div className="coding-output">
              {run.compileOutput && (
                <div className="coding-output-block error">
                  <span>Compile output</span>
                  <pre>{run.compileOutput}</pre>
                </div>
              )}
              <div className="coding-output-block">
                <span>stdout</span>
                <pre>{run.stdout || "(no output)"}</pre>
              </div>
              {run.stderr && (
                <div className="coding-output-block error">
                  <span>stderr</span>
                  <pre>{run.stderr}</pre>
                </div>
              )}
              {run.expectedOutput != null && (
                <p className={`coding-expected ${run.matchesExpected ? "pass" : "fail"}`}>
                  {run.matchesExpected ? <FiCheckCircle /> : <FiXCircle />}
                  {run.matchesExpected ? "Matches the expected output for this example." : `Expected output: ${run.expectedOutput || "(empty)"}`}
                </p>
              )}
              <p className="coding-run-meta">
                Time: {run.executionTimeMs != null ? `${run.executionTimeMs} ms` : "–"} · Memory: {formatMemory(run.memoryKb)}
              </p>
            </div>
          )}

          {submission && (
            <>
              <p className="coding-submit-summary">
                Passed {submission.passedTests} / {submission.totalTests} tests · Score: {submission.score}%
              </p>
              <ul className="coding-tests">
                {submission.tests.map((test) => (
                  <li key={test.index} className={test.passed ? "pass" : "fail"}>
                    {test.passed ? <FiCheckCircle /> : <FiXCircle />}
                    <span>Hidden test {test.index}</span>
                    <code>{TEST_STATUS_LABELS[test.status] || test.status}{test.executionTimeMs != null ? ` · ${test.executionTimeMs} ms` : ""}</code>
                  </li>
                ))}
              </ul>
              {submission.compileOutput && (
                <div className="coding-output-block error">
                  <span>Compile output</span>
                  <pre>{submission.compileOutput}</pre>
                </div>
              )}
              {submission.errorMessage && <p className="coding-console-warning">{submission.errorMessage}</p>}
            </>
          )}

          {message && loadState === "ready" && <p className="coding-console-warning" role="alert">{message}</p>}

        </div>


        <div className="coding-actions">

          {submission ? (
            <button type="button" className="primary-button" onClick={finish}>
              See results
              <FiArrowRight />
            </button>
          ) : (
            <>
              <button type="button" className="coding-run-button" onClick={handleRun} disabled={!canExecute}>
                {running ? <FiLoader className="resume-spin" /> : <FiPlay />}
                {running ? "Running…" : "Run Code"}
              </button>

              <button type="button" className="primary-button" onClick={handleSubmit} disabled={!canExecute}>
                {submitting ? <FiLoader className="resume-spin" /> : <FiSend />}
                {submitting ? "Submitting…" : "Submit Solution"}
              </button>
            </>
          )}

        </div>

      </section>

    </div>
  );
}

export default CodingWorkspace;
