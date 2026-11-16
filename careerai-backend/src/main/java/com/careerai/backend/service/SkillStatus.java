package com.careerai.backend.service;

/**
 * Skill status shared by Performance, Skill Gap and Plan.
 * The thresholds live only here so they can be tuned in one place.
 */
public enum SkillStatus {

    STRONG,
    GOOD,
    IMPROVE,
    PRIORITY,

    /** Listed in the profile but not yet measured by practice. Has no score. */
    SELF_REPORTED,

    /** No practice data and not in the profile. Has no score. */
    UNASSESSED;

    public static final int STRONG_THRESHOLD = 80;
    public static final int GOOD_THRESHOLD = 65;
    public static final int IMPROVE_THRESHOLD = 50;

    /** Status for a measured score; a null score is UNASSESSED. */
    public static SkillStatus fromScore(Integer score) {
        if (score == null) return UNASSESSED;
        if (score >= STRONG_THRESHOLD) return STRONG;
        if (score >= GOOD_THRESHOLD) return GOOD;
        if (score >= IMPROVE_THRESHOLD) return IMPROVE;
        return PRIORITY;
    }

    /** True for statuses that come from a real practice score. */
    public boolean isAssessed() {
        return this != SELF_REPORTED && this != UNASSESSED;
    }

    /** Assessed and below GOOD: needs attention. */
    public boolean needsWork() {
        return this == IMPROVE || this == PRIORITY;
    }
}
