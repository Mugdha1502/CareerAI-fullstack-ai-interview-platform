package com.careerai.backend.billing;

/**
 * Every plan-controlled capability. Metered features have a per-period limit and are counted only
 * for meaningful, resource-consuming actions (never page views); access features describe the
 * level a plan gets (e.g. BASIC / FULL analytics) and are not counted.
 */
public enum FeatureKey {
    AI_TEXT_INTERVIEW("Text interviews", true),
    AI_VOICE_INTERVIEW("Voice interviews", true),
    AI_COACH_MESSAGE("AI Coach messages", true),
    RESUME_ATS_ANALYSIS("ATS analyses", true),
    RESUME_JOB_MATCH("Job matches", true),
    RESUME_AI_IMPROVEMENT("AI resume improvements", true),
    RESUME_INTERVIEW_PREP("Interview preparations", true),
    CODING_RUN("Coding runs", true),
    CODING_SUBMIT("Coding submissions", true),
    SQL_RUN("SQL runs", true),
    SQL_SUBMIT("SQL submissions", true),
    PRACTICE_MODULES("Practice modules", false),
    PERFORMANCE_ANALYTICS("Performance analytics", false),
    SKILL_GAP("Skill gap analysis", false),
    MY_PLAN("My Plan", false);

    private final String label;
    private final boolean metered;

    FeatureKey(String label, boolean metered) {
        this.label = label;
        this.metered = metered;
    }

    public String label() {
        return label;
    }

    public boolean metered() {
        return metered;
    }
}
