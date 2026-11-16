package com.careerai.backend.billing;

import com.careerai.backend.entity.Plan;

/**
 * The user's plan allowance for a feature is used up for the current period (HTTP 429,
 * code PLAN_LIMIT_REACHED). Carries what the frontend needs for an upgrade prompt.
 */
public class PlanLimitExceededException extends RuntimeException {

    private final FeatureKey feature;
    private final Plan plan;
    private final int limit;
    private final int used;
    private final UsagePeriod period;
    private final Integer proLimit;

    public PlanLimitExceededException(FeatureKey feature, Plan plan, int limit, int used, UsagePeriod period, Integer proLimit) {
        super(message(feature, limit, period));
        this.feature = feature;
        this.plan = plan;
        this.limit = limit;
        this.used = used;
        this.period = period;
        this.proLimit = proLimit;
    }

    private static String message(FeatureKey feature, int limit, UsagePeriod period) {
        String window = period == UsagePeriod.DAILY ? "today's" : "this month's";
        return "You've used " + window + " " + limit + " " + feature.label().toLowerCase() + ".";
    }

    public FeatureKey feature() {
        return feature;
    }

    public Plan plan() {
        return plan;
    }

    public int limit() {
        return limit;
    }

    public int used() {
        return used;
    }

    public UsagePeriod period() {
        return period;
    }

    /** The PRO allowance for the same feature (null when the user is already on PRO). */
    public Integer proLimit() {
        return proLimit;
    }
}
