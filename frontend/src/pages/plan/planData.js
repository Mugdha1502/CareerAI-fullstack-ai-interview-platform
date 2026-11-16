import {
  getDemoSkillGap,
  getPracticeLink,
  findTopicForSkill
} from "../career/careerTrackData";

/* =====================================================
   PREPARATION PLAN (frontend demo)

   TODO backend: GET /api/plan/today and PATCH /api/plan/tasks/{id}
   will replace the template and localStorage below.
===================================================== */

export const PLAN_TASKS_KEY = "careerai_plan_tasks";

export const TASK_STATUSES = [
  { value: "not-started", label: "Not Started" },
  { value: "in-progress", label: "In Progress" },
  { value: "completed", label: "Completed" }
];

export const WEEK_DAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

/** Local calendar date, e.g. "2026-10-20" (the user's day, not UTC). */
export function toDateKey(date = new Date()) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

/** Monday-to-Sunday dates for the week containing `date`. */
export function getWeekDates(date = new Date()) {
  const monday = new Date(date);
  const offset = (monday.getDay() + 6) % 7;
  monday.setDate(monday.getDate() - offset);
  monday.setHours(0, 0, 0, 0);

  return WEEK_DAYS.map((label, index) => {
    const day = new Date(monday);
    day.setDate(monday.getDate() + index);
    return { label, date: day, key: toDateKey(day) };
  });
}


/* ---------- Priority skills ---------- */

const STATUS_RANK = { Priority: 0, Improve: 1, Good: 2, Strong: 3 };

/**
 * Lowest-scoring skills for the track, each with its best destination.
 * Same ordering as Skill Gap: Priority before Improve, then earliest roadmap topic.
 */
export function getPrioritySkills(track, count = 3) {
  const { skills } = getDemoSkillGap(track);

  return [...skills]
    .sort((a, b) => a.value - b.value)
    .slice(0, count)
    .map((skill) => {
      const practice = getPracticeLink(skill.name);
      const topic = findTopicForSkill(track, skill.name);

      return {
        ...skill,
        practicePath: practice?.path || null,
        topicPath: topic ? `/career-tracks/${track.id}/topic/${topic.id}` : null,
        purpose: topic?.purpose || null,
        roadmapIndex: topic ? track.roadmap.indexOf(topic) : Infinity
      };
    })
    .sort((a, b) =>
      STATUS_RANK[a.status] - STATUS_RANK[b.status] || a.roadmapIndex - b.roadmapIndex
    );
}


/* ---------- Daily task template ---------- */

const mentions = (track, pattern) =>
  [...track.skills, ...track.roadmap.map((step) => step.title)].some((label) => pattern.test(label));

export function buildDailyTasks(track) {
  const coreTopic = track.practiceTopics[0];
  const priority = getPrioritySkills(track, 3).find(
    (skill) => skill.practicePath && skill.name !== coreTopic
  ) || getPrioritySkills(track, 1)[0];

  const usesSql = mentions(track, /\bsql\b|mysql/i);
  const usesCoding = ["development", "data"].includes(track.domain);

  return [
    {
      id: "core-topic",
      title: track.id === "java-full-stack" ? "Java Collections" : `${coreTopic} Practice`,
      category: "Technical Practice",
      description: `Core ${coreTopic} questions for ${track.title} interviews.`,
      minutes: 20,
      to: `/practice/technical?topic=${encodeURIComponent(coreTopic)}`
    },
    {
      id: "priority-skill",
      title: priority.name,
      category: "Priority Skill",
      description: priority.purpose || `Your lowest-scoring skill in the sample data (${priority.value}%).`,
      minutes: 25,
      to: priority.practicePath || priority.topicPath || `/career-tracks/${track.id}`
    },
    usesSql
      ? {
          id: "sql",
          title: "SQL Practice",
          category: "SQL",
          description: "Write queries on a practice dataset — joins and aggregation.",
          minutes: 20,
          to: "/practice/sql"
        }
      : {
          id: "aptitude",
          title: "Aptitude Practice",
          category: "Aptitude",
          description: "A short timed set to keep reasoning skills sharp.",
          minutes: 15,
          to: "/practice/aptitude"
        },
    usesCoding
      ? {
          id: "coding",
          title: "Coding Practice",
          category: "Coding",
          description: "Solve one problem and review edge cases.",
          minutes: 30,
          to: "/practice/coding"
        }
      : {
          id: "daily-challenge",
          title: "Daily Challenge",
          category: "Daily Challenge",
          description: "Four mixed questions to keep your streak going.",
          minutes: 8,
          to: "/practice/daily-challenge"
        },
    {
      id: "interview",
      title: "AI Interview",
      category: "Interview",
      description: `A role-based mock interview for ${track.title}.`,
      minutes: 20,
      to: "/ai-interview/setup?type=role",
      state: { source: "Role-Based", targetRole: track.title }
    },
    {
      id: "resume",
      title: "Resume Improvement",
      category: "Resume",
      description: "Strengthen one section of your resume wording.",
      minutes: 15,
      to: "/resume/ai-improvement"
    }
  ];
}

/** Focus shown on each weekday card. */
export function buildWeeklyFocus(track) {
  const tasks = buildDailyTasks(track);
  const byId = Object.fromEntries(tasks.map((task) => [task.id, task.title]));

  return [
    byId["core-topic"],
    byId["priority-skill"],
    byId.sql || byId.aptitude,
    byId.coding || byId["daily-challenge"],
    "AI Interview",
    byId.resume,
    "Weekly Review"
  ];
}


/* ---------- Task status storage ---------- */

function readAll() {
  try {
    const parsed = JSON.parse(localStorage.getItem(PLAN_TASKS_KEY) || "{}");
    return parsed && typeof parsed === "object" ? parsed : {};
  } catch {
    return {};
  }
}

function writeAll(data) {
  try {
    localStorage.setItem(PLAN_TASKS_KEY, JSON.stringify(data));
  } catch {
    // Storage unavailable — task state lasts only for this page view.
  }
}

/** { [dateKey]: { [taskId]: status } } for one user. */
export function getUserTaskHistory(userId) {
  if (!userId) return {};
  return readAll()[userId] || {};
}

export function getTaskStatuses(userId, dateKey = toDateKey()) {
  return getUserTaskHistory(userId)[dateKey] || {};
}

export function setTaskStatus(userId, taskId, status, dateKey = toDateKey()) {
  if (!userId) return {};

  const all = readAll();
  const history = { ...(all[userId] || {}) };
  const day = { ...(history[dateKey] || {}) };

  if (status === "not-started") delete day[taskId];
  else day[taskId] = status;

  history[dateKey] = day;

  // Keep roughly the last five weeks.
  const cutoff = new Date();
  cutoff.setDate(cutoff.getDate() - 35);
  const cutoffKey = toDateKey(cutoff);

  Object.keys(history).forEach((key) => {
    if (key < cutoffKey) delete history[key];
  });

  all[userId] = history;
  writeAll(all);

  return day;
}

/** Number of completed tasks per date, e.g. { "2026-10-20": 3 }. */
export function getCompletedCounts(userId) {
  const history = getUserTaskHistory(userId);

  return Object.fromEntries(
    Object.entries(history).map(([key, day]) => [
      key,
      Object.values(day).filter((status) => status === "completed").length
    ])
  );
}

export function clearPlanHistory(userId) {
  const all = readAll();
  delete all[userId];
  writeAll(all);
}
