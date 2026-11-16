export const PLAN_LIMITS = {

  GUEST: {
    AI_INTERVIEW: 1,
    TECHNICAL_PRACTICE: 5,
    CODING_PRACTICE: 3,
    SQL_PRACTICE: 3,
    APTITUDE: 5,
    RESUME_ANALYSIS: 1,
    JOB_MATCH: 1,
    AI_COACH: 3
  },


  FREE: {
    AI_INTERVIEW: 10,
    TECHNICAL_PRACTICE: 50,
    CODING_PRACTICE: 15,
    SQL_PRACTICE: 15,
    APTITUDE: 30,
    RESUME_ANALYSIS: 2,
    JOB_MATCH: 3,
    AI_COACH: 10
  },


  PRO: {
    AI_INTERVIEW: 50,
    TECHNICAL_PRACTICE: 500,
    CODING_PRACTICE: 100,
    SQL_PRACTICE: 100,
    APTITUDE: 200,
    RESUME_ANALYSIS: 20,
    JOB_MATCH: 30,
    AI_COACH: 100
  },


  PREMIUM: {
    AI_INTERVIEW: Infinity,
    TECHNICAL_PRACTICE: Infinity,
    CODING_PRACTICE: Infinity,
    SQL_PRACTICE: Infinity,
    APTITUDE: Infinity,
    RESUME_ANALYSIS: Infinity,
    JOB_MATCH: Infinity,
    AI_COACH: Infinity
  }

};


function getToday() {
  return new Date()
    .toISOString()
    .slice(0, 10);
}


function usageKey(
  userId,
  feature
) {

  return (
    `careerai_usage_` +
    `${userId}_` +
    `${feature}_` +
    `${getToday()}`
  );

}


export function getFeatureLimit(
  plan,
  feature
) {

  return (
    PLAN_LIMITS[plan]?.[feature] ??
    0
  );

}


export function getFeatureUsage(
  userId,
  feature
) {

  if (!userId) {
    return 0;
  }


  const value =
    localStorage.getItem(
      usageKey(
        userId,
        feature
      )
    );


  return Number(
    value || 0
  );
}


export function checkFeatureAccess(
  user,
  feature
) {

  if (!user) {

    return {
      allowed: false,
      reason: "LOGIN_REQUIRED",
      used: 0,
      limit: 0,
      remaining: 0
    };

  }


  const limit =
    getFeatureLimit(
      user.plan,
      feature
    );


  if (limit === Infinity) {

    return {
      allowed: true,
      reason: null,
      used: 0,
      limit: Infinity,
      remaining: Infinity
    };

  }


  const used =
    getFeatureUsage(
      user.id,
      feature
    );


  const remaining =
    Math.max(
      limit - used,
      0
    );


  return {

    allowed:
      used < limit,

    reason:
      used >= limit
        ? "LIMIT_REACHED"
        : null,

    used,

    limit,

    remaining

  };

}


export function recordFeatureUsage(
  user,
  feature
) {

  const access =
    checkFeatureAccess(
      user,
      feature
    );


  if (!access.allowed) {
    return access;
  }


  if (
    access.limit ===
    Infinity
  ) {
    return access;
  }


  const newUsed =
    access.used + 1;


  localStorage.setItem(
    usageKey(
      user.id,
      feature
    ),
    String(newUsed)
  );


  return {

    ...access,

    used: newUsed,

    remaining:
      Math.max(
        access.limit -
          newUsed,
        0
      )

  };

}


export function getUsageSummary(
  user
) {

  if (!user) {
    return [];
  }


  const features = [

    {
      code: "AI_INTERVIEW",
      label: "AI Interviews"
    },

    {
      code: "CODING_PRACTICE",
      label: "Coding"
    },

    {
      code: "SQL_PRACTICE",
      label: "SQL Practice"
    },

    {
      code: "TECHNICAL_PRACTICE",
      label: "Technical Practice"
    }

  ];


  return features.map(
    (item) => {

      const access =
        checkFeatureAccess(
          user,
          item.code
        );


      return {
        ...item,
        ...access
      };

    }
  );

}