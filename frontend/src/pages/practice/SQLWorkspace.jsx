import { useEffect, useState } from "react";
import {
  FiDatabase,
  FiPlay,
  FiSend,
  FiRotateCcw,
  FiShield,
  FiChevronDown,
  FiAlertCircle,
  FiHelpCircle,
  FiLoader,
  FiCheckCircle,
  FiArrowRight
} from "react-icons/fi";
import { getSqlProblem } from "./practiceProvider";
import {
  canSavePractice,
  executeSql,
  executionError,
  getSqlQuestion,
  submitSql
} from "../../services/practiceService";

/* Queries run only in the backend's disposable sandbox database (one fresh in-memory database
   per run), never against the CareerAI application database. Run Query is never saved; Submit
   Query compares your result with the expected result and is saved as the practice result. */

const STARTER_QUERY = "-- Write a read-only query (SELECT or WITH)\nSELECT ";

const SUBMIT_LABELS = {
  CORRECT: "Correct",
  PARTIAL: "Partially correct",
  INCORRECT: "Incorrect",
  ERROR: "Query error"
};

function cellText(cell) {
  return cell === null || cell === undefined ? "NULL" : String(cell);
}

function ResultTable({ result }) {
  return (
    <div className="sql-result">
      <div className="sql-result-head">
        <strong>Result</strong>
        <span>
          {result.rowCount} row{result.rowCount === 1 ? "" : "s"}
          {result.truncated ? " (first rows only)" : ""} · {result.executionTimeMs} ms
        </span>
      </div>
      <div className="practice-table-wrap sql-result-scroll">
        <table className="practice-data-table">
          <thead>
            <tr>
              {result.columns.map((column, index) => (
                <th key={`${index}-${column}`}>{column}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {result.rows.map((row, rowIndex) => (
              <tr key={rowIndex}>
                {row.map((cell, index) => (
                  <td key={index}>{cellText(cell)}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {result.rowCount === 0 && <p className="sql-result-note">The query returned no rows.</p>}
    </div>
  );
}

function SQLWorkspace({ config, onComplete, getRemoteSession }) {
  const signedIn = canSavePractice();
  const [local] = useState(() => getSqlProblem(config.dataset, config.topic));
  const [question, setQuestion] = useState(null);
  const [loadState, setLoadState] = useState(signedIn ? "loading" : "guest"); // loading | ready | guest | error
  const [query, setQuery] = useState(STARTER_QUERY);
  const [output, setOutput] = useState(null);
  const [submission, setSubmission] = useState(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(null); // null | "run" | "submit"
  const [openTable, setOpenTable] = useState(local.dataset?.tables[0]?.name || "");
  const [showHint, setShowHint] = useState(false);

  useEffect(() => {
    if (!signedIn) return undefined;
    let cancelled = false;
    getSqlQuestion({ dataset: config.dataset, topic: config.topic })
      .then((data) => {
        if (cancelled) return;
        setQuestion(data);
        setOpenTable(data.tables[0]?.name || "");
        setLoadState("ready");
      })
      .catch((err) => {
        if (cancelled) return;
        setError(executionError(err).message);
        setLoadState("error");
      });
    return () => {
      cancelled = true;
    };
  }, [signedIn, config.dataset, config.topic]);

  // Server question (sample rows come from the sandbox seed) or the local preview for guests.
  const tables = question
    ? question.tables.map((table) => ({ name: table.name, columns: table.columns, headers: table.sampleColumns, rows: table.sampleRows }))
    : (local.dataset?.tables || []).map((table) => ({ name: table.name, columns: table.columns, headers: table.columns.map((c) => c.split(" ")[0]), rows: table.sample }));
  const description = question ? question.datasetDescription : local.dataset?.description;
  const prompt = question ? question.question : local.problem?.question;
  const hint = question ? question.hint : local.problem?.hint;
  const canExecute = loadState === "ready" && !busy && !submission;

  const handleRun = async () => {
    if (!canExecute) return;
    setBusy("run");
    setError("");
    setOutput(null);
    try {
      setOutput(await executeSql({ questionId: question.id, query }));
    } catch (err) {
      setError(executionError(err).message);
    } finally {
      setBusy(null);
    }
  };

  const handleReset = () => {
    setQuery(STARTER_QUERY);
    setOutput(null);
    setError("");
  };

  const handleSubmit = async () => {
    if (!canExecute) return;
    setBusy("submit");
    setError("");
    try {
      const session = getRemoteSession ? await getRemoteSession() : null;
      const result = await submitSql({ questionId: question.id, query, sessionId: session?.id });
      setSubmission(result);
      if (result.output) setOutput(result.output);
    } catch (err) {
      setError(executionError(err).message);
    } finally {
      setBusy(null);
    }
  };

  const finish = () => {
    onComplete({
      title: "Practice Complete",
      scoreLabel: "Result checks passed",
      score: submission.passedChecks,
      total: submission.totalChecks,
      strongArea: config.topic,
      weakArea: submission.status === "CORRECT" ? "None in this session" : config.topic,
      note: `${SUBMIT_LABELS[submission.status] || submission.status} · Score ${submission.score}% (checked on the sample data and on hidden data)`,
      xpActivity: "SQL_PROBLEM",
      savedByServer: true
    });
  };

  return (
    <div className="sql-layout">

      {/* DATASET */}

      <aside className="sql-dataset">

        <div className="sql-dataset-head">
          <FiDatabase />
          <div>
            <strong>{config.dataset}</strong>
            <span>{description}</span>
          </div>
        </div>

        <p className="sql-dataset-note">
          <FiShield />
          Isolated practice database — read-only, recreated for every query
        </p>

        {tables.map((table) => {
          const isOpen = openTable === table.name;

          return (
            <div key={table.name} className={`sql-table-card${isOpen ? " open" : ""}`}>

              <button
                type="button"
                className="sql-table-toggle"
                aria-expanded={isOpen}
                onClick={() => setOpenTable(isOpen ? "" : table.name)}
              >
                <code>{table.name}</code>
                <FiChevronDown />
              </button>

              {isOpen && (
                <div className="sql-table-body">

                  <ul className="sql-columns">
                    {table.columns.map((column) => (
                      <li key={column}>{column}</li>
                    ))}
                  </ul>

                  <div className="practice-table-wrap">
                    <table className="practice-data-table compact">
                      <thead>
                        <tr>
                          {table.headers.map((column) => (
                            <th key={column}>{column}</th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {table.rows.map((row, rowIndex) => (
                          <tr key={rowIndex}>
                            {row.map((cell, index) => (
                              <td key={index}>{cellText(cell)}</td>
                            ))}
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>

                </div>
              )}

            </div>
          );
        })}

      </aside>


      {/* CONSOLE */}

      <section className="sql-workspace">

        <div className="sql-question">

          <span className="quiz-runner-heading">
            {config.topic} • {config.difficulty}
          </span>

          <h2>{prompt}</h2>

          <button
            type="button"
            className="sql-hint-toggle"
            aria-expanded={showHint}
            onClick={() => setShowHint((value) => !value)}
          >
            <FiHelpCircle />
            {showHint ? "Hide hint" : "Show hint"}
          </button>

          {showHint && <p className="sql-hint">{hint}</p>}

        </div>

        {loadState === "loading" && <p className="sql-result-note"><FiLoader className="resume-spin" /> Loading the problem…</p>}
        {loadState === "guest" && <p className="sql-result-note">Sign in to run and submit queries. Queries run in an isolated practice database on the server.</p>}

        <div className="sql-console">

          <div className="sql-console-bar">
            <span>query.sql</span>
            <span className="coding-demo-pill">
              <FiShield />
              {question ? "Sandboxed execution" : "Preview"}
            </span>
          </div>

          <textarea
            aria-label="SQL editor"
            spellCheck={false}
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            readOnly={Boolean(submission)}
          />

        </div>


        <div className="sql-actions">

          {submission ? (
            <button type="button" className="primary-button" onClick={finish}>
              See results
              <FiArrowRight />
            </button>
          ) : (
            <>
              <button type="button" className="coding-run-button" onClick={handleRun} disabled={!canExecute}>
                {busy === "run" ? <FiLoader className="resume-spin" /> : <FiPlay />}
                {busy === "run" ? "Running…" : "Run Query"}
              </button>

              <button type="button" className="secondary-button" onClick={handleReset} disabled={Boolean(busy)}>
                <FiRotateCcw />
                Reset
              </button>

              <button type="button" className="primary-button" onClick={handleSubmit} disabled={!canExecute}>
                {busy === "submit" ? <FiLoader className="resume-spin" /> : <FiSend />}
                {busy === "submit" ? "Submitting…" : "Submit Query"}
              </button>
            </>
          )}

        </div>


        {error && (
          <p className="sql-error" role="alert">
            <FiAlertCircle />
            {error}
          </p>
        )}

        {submission && (
          <p className={`sql-submit-summary ${submission.status === "CORRECT" ? "pass" : "fail"}`} role="status">
            {submission.status === "CORRECT" ? <FiCheckCircle /> : <FiAlertCircle />}
            <span>
              <strong>{SUBMIT_LABELS[submission.status] || submission.status} · Score: {submission.score}%</strong>
              {" "}{submission.errorMessage || submission.message}
            </span>
          </p>
        )}

        {output && <ResultTable result={output} />}

      </section>

    </div>
  );
}

export default SQLWorkspace;
