// Frontend demo evaluators.
//
// Each function mirrors a future backend endpoint and returns the same shape
// the real service will return:
//   descriptive answers  → Spring Boot → Gemini (structured evaluation)
//   code runs            → Spring Boot → secure sandbox / code runner
//   SQL queries          → Spring Boot → isolated practice database
//   system design        → Spring Boot → Gemini rubric
//   voice answers        → realtime voice service (Vapi) + evaluation
//
// Nothing here executes user code or SQL. Never use eval() or new Function().

/* ---------- Descriptive answers ---------- */

// TODO backend: POST /api/practice/descriptive/evaluate { questionId, answer }
export function evaluateDescriptiveAnswer(prompt, answer) {
  const text = answer.toLowerCase();
  const wordCount = answer.trim().split(/\s+/).filter(Boolean).length;

  const covered = prompt.keyPoints.filter((point) =>
    point.keywords.some((keyword) => text.includes(keyword.toLowerCase()))
  );

  const missing = prompt.keyPoints.filter((point) => !covered.includes(point));

  return {
    covered: covered.map((point) => point.label),
    missing: missing.map((point) => point.label),
    score: covered.length,
    total: prompt.keyPoints.length,
    wordCount,
    lengthNote:
      wordCount < 25
        ? "Your answer is quite short. Aim for 60–150 words and include a concrete example."
        : null,
    strongerAnswer: prompt.strongerAnswer
  };
}

/* ---------- Coding ---------- */

// TODO backend: POST /api/practice/coding/run { problemId, language, code }
// The backend will execute code in an isolated sandbox with time/memory limits.
export function runCodeMock(problem, code, starterCode) {
  const unchanged = code.trim() === starterCode.trim();
  const tooShort = code.trim().length < starterCode.trim().length + 15;

  if (unchanged || tooShort) {
    return {
      status: "not-implemented",
      message: "The starter code returns a placeholder value. Write your solution and run again.",
      results: problem.tests.map((test) => ({
        ...test,
        actual: "placeholder output",
        passed: false
      }))
    };
  }

  // Demo outcome: common cases pass, the edge case is reported as failing so
  // the result screen and console states can be exercised.
  return {
    status: "completed",
    message: "Demo execution finished.",
    results: problem.tests.map((test, index) => ({
      ...test,
      actual: index < 2 ? test.expected : "Different output (edge case)",
      passed: index < 2
    }))
  };
}

/* ---------- SQL ---------- */

const blockedKeywords = /\b(INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|CREATE|GRANT|REVOKE|REPLACE|MERGE|CALL|EXEC)\b/i;

function stripSqlComments(query) {
  return query.replace(/--.*$/gm, "").replace(/\/\*[\s\S]*?\*\//g, "").trim();
}

export function validateSqlQuery(query) {
  const cleaned = stripSqlComments(query);

  if (!cleaned) {
    return { ok: false, error: "Write a query before running it." };
  }

  if (!/^(SELECT|WITH)\b/i.test(cleaned)) {
    return { ok: false, error: "Practice mode only accepts read-only queries that start with SELECT or WITH." };
  }

  if (blockedKeywords.test(cleaned)) {
    return { ok: false, error: "Data-changing statements are not allowed in practice mode." };
  }

  if (cleaned.replace(/;\s*$/, "").includes(";")) {
    return { ok: false, error: "Run one statement at a time." };
  }

  return { ok: true, error: null };
}

// TODO backend: POST /api/practice/sql/run { datasetId, problemId, query }
// Runs against a sandboxed copy of the practice dataset only.
export function runSqlMock(problem, query) {
  const validation = validateSqlQuery(query);

  if (!validation.ok) {
    return { ok: false, error: validation.error };
  }

  return {
    ok: true,
    columns: problem.expected.columns,
    rows: problem.expected.rows
  };
}

// TODO backend: POST /api/practice/sql/submit — compares real result sets.
export function checkSqlSubmission(problem, query) {
  const validation = validateSqlQuery(query);

  if (!validation.ok) {
    return { passed: false, missing: [], error: validation.error };
  }

  const normalised = stripSqlComments(query).toUpperCase().replace(/\s+/g, " ");
  const missing = problem.mustInclude.filter((keyword) => !normalised.includes(keyword));

  return { passed: missing.length === 0, missing, error: null };
}

/* ---------- System design ---------- */

// TODO backend: POST /api/practice/system-design/review { challengeId, sections }
export function reviewSystemDesign(sections, answers) {
  const rubric = sections.map((section) => {
    const words = (answers[section.id] || "").trim().split(/\s+/).filter(Boolean).length;

    if (words >= 25) {
      return { ...section, status: "covered", statusLabel: "Covered", words };
    }

    if (words > 0) {
      return { ...section, status: "partial", statusLabel: "Needs more detail", words };
    }

    return { ...section, status: "missing", statusLabel: "Missing", words };
  });

  return {
    rubric,
    score: rubric.filter((item) => item.status === "covered").length,
    total: rubric.length
  };
}

/* ---------- Voice (typed fallback) ---------- */

// TODO voice: replace with realtime voice transcription + evaluation service.
export function evaluateVoiceAnswer(topicData, answer) {
  const sentences = answer.split(/[.!?]+/).map((s) => s.trim()).filter(Boolean);
  const words = answer.trim().split(/\s+/).filter(Boolean);
  const averageLength = sentences.length ? words.length / sentences.length : 0;
  const text = answer.toLowerCase();
  const keywordHits = topicData.keywords.filter((keyword) => text.includes(keyword)).length;

  const structure =
    sentences.length >= 4 ? "Good" : sentences.length >= 2 ? "Fair" : "Needs work";

  const clarity =
    averageLength >= 8 && averageLength <= 25 ? "Good" : averageLength > 25 ? "Fair" : "Needs work";

  const relevance =
    keywordHits >= 3 ? "Good" : keywordHits >= 1 ? "Fair" : "Needs work";

  const suggestions = [];

  if (structure !== "Good") {
    suggestions.push("Organise your answer into a clear beginning, middle and end (or Situation–Task–Action–Result).");
  }

  if (clarity !== "Good") {
    suggestions.push(
      averageLength > 25
        ? "Break long sentences into shorter ones so they are easier to follow when spoken."
        : "Expand short statements with a reason or an example."
    );
  }

  if (relevance !== "Good") {
    suggestions.push("Connect your answer more directly to the question with specific details from your experience.");
  }

  if (suggestions.length === 0) {
    suggestions.push("Strong answer. Practise saying it aloud in under 90 seconds and keep a confident pace.");
  }

  const points = [structure, clarity, relevance].filter((value) => value === "Good").length;

  return {
    structure,
    clarity,
    relevance,
    suggestions,
    wordCount: words.length,
    score: points,
    total: 3
  };
}
