const XP_KEY_PREFIX =
  "careerai_xp_";

const XP_HISTORY_PREFIX =
  "careerai_xp_history_";

const STREAK_PREFIX =
  "careerai_streak_";


const LEVELS = [

  {
    level: 1,
    name: "Career Starter",
    minimumXP: 0
  },

  {
    level: 2,
    name: "Career Explorer",
    minimumXP: 100
  },

  {
    level: 3,
    name: "Active Learner",
    minimumXP: 250
  },

  {
    level: 4,
    name: "Skill Builder",
    minimumXP: 500
  },

  {
    level: 5,
    name: "Career Builder",
    minimumXP: 900
  },

  {
    level: 6,
    name: "Interview Ready",
    minimumXP: 1400
  },

  {
    level: 7,
    name: "Job Ready",
    minimumXP: 2000
  },

  {
    level: 8,
    name: "Career Pro",
    minimumXP: 2800
  },

  {
    level: 9,
    name: "Career Expert",
    minimumXP: 3800
  },

  {
    level: 10,
    name: "Career Master",
    minimumXP: 5000
  }

];


export const XP_REWARDS = {

  DAILY_LOGIN: 5,

  DAILY_CHALLENGE: 20,

  TECHNICAL_SET: 25,

  APTITUDE_SET: 20,

  CODING_PROBLEM: 30,

  SQL_PROBLEM: 25,

  MOCK_ASSESSMENT: 50,

  AI_INTERVIEW: 75,

  INTERVIEW_80_BONUS: 25,

  RESUME_COMPLETE: 40,

  RESUME_ANALYSIS: 20,

  ROADMAP_STEP: 30,

  PROFILE_COMPLETE: 30

};


function xpKey(userId) {
  return `${XP_KEY_PREFIX}${userId}`;
}


function historyKey(userId) {
  return `${XP_HISTORY_PREFIX}${userId}`;
}


function streakKey(userId) {
  return `${STREAK_PREFIX}${userId}`;
}


export function getXP(
  userId
) {

  if (!userId) {
    return 0;
  }


  return Number(
    localStorage.getItem(
      xpKey(userId)
    ) || 0
  );

}


export function getXPHistory(
  userId
) {

  try {

    return JSON.parse(
      localStorage.getItem(
        historyKey(userId)
      ) || "[]"
    );

  } catch {

    return [];

  }

}


export function addXP(
  userId,
  amount,
  activity,
  description
) {

  const currentXP =
    getXP(userId);


  const newXP =
    currentXP + amount;


  localStorage.setItem(
    xpKey(userId),
    String(newXP)
  );


  const history =
    getXPHistory(userId);


  history.unshift({

    id:
      `xp_${Date.now()}`,

    amount,

    activity,

    description,

    createdAt:
      new Date().toISOString()

  });


  localStorage.setItem(
    historyKey(userId),
    JSON.stringify(
      history.slice(0, 50)
    )
  );


  return newXP;
}


export function getLevelInfo(
  xp
) {

  let current =
    LEVELS[0];


  for (
    const level
    of LEVELS
  ) {

    if (
      xp >= level.minimumXP
    ) {

      current = level;

    }

  }


  const currentIndex =
    LEVELS.findIndex(
      (item) =>
        item.level ===
        current.level
    );


  const next =
    LEVELS[
      currentIndex + 1
    ] || null;


  if (!next) {

    return {

      ...current,

      nextLevel: null,

      nextXP: current.minimumXP,

      progress: 100,

      remainingXP: 0

    };

  }


  const range =
    next.minimumXP -
    current.minimumXP;


  const earned =
    xp -
    current.minimumXP;


  const progress =
    Math.min(
      Math.round(
        (earned / range) *
          100
      ),
      100
    );


  return {

    ...current,

    nextLevel:
      next.level,

    nextName:
      next.name,

    nextXP:
      next.minimumXP,

    progress,

    remainingXP:
      Math.max(
        next.minimumXP -
          xp,
        0
      )

  };

}


export function getStreak(
  userId
) {

  try {

    return JSON.parse(
      localStorage.getItem(
        streakKey(userId)
      ) ||
      JSON.stringify({
        current: 0,
        longest: 0,
        lastActivityDate: null
      })
    );

  } catch {

    return {
      current: 0,
      longest: 0,
      lastActivityDate: null
    };

  }

}


export function recordDailyActivity(
  userId
) {

  const streak =
    getStreak(userId);


  const today =
    new Date();


  const todayString =
    today
      .toISOString()
      .slice(0, 10);


  if (
    streak.lastActivityDate ===
    todayString
  ) {

    return streak;

  }


  let current = 1;


  if (
    streak.lastActivityDate
  ) {

    const last =
      new Date(
        streak.lastActivityDate
      );


    const difference =
      Math.round(
        (
          today.setHours(
            0,
            0,
            0,
            0
          ) -
          last.setHours(
            0,
            0,
            0,
            0
          )
        ) /
        86400000
      );


    if (difference === 1) {

      current =
        streak.current + 1;

    }

  }


  const updated = {

    current,

    longest:
      Math.max(
        current,
        streak.longest
      ),

    lastActivityDate:
      todayString

  };


  localStorage.setItem(
    streakKey(userId),
    JSON.stringify(updated)
  );


  return updated;
}