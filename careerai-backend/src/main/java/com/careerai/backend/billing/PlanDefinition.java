package com.careerai.backend.billing;

import com.careerai.backend.entity.Plan;

import java.util.Map;

/** What one plan includes: metered limits and access levels. Built by PlanCatalog only. */
public record PlanDefinition(Plan plan, Map<FeatureKey, Limit> limits, Map<FeatureKey, String> access) {

    /** A metered allowance: limit uses per period. */
    public record Limit(int limit, UsagePeriod period) {
    }

    public Limit limitFor(FeatureKey feature) {
        return limits.get(feature);
    }
}
