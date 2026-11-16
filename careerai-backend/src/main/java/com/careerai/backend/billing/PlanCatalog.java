package com.careerai.backend.billing;

import com.careerai.backend.entity.Plan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * The ONE place plan limits and PRO prices are defined. Every module, the entitlement API and the
 * pricing page read from here; the React app never hardcodes limits or prices.
 *
 * Defaults below can be overridden per plan/feature without code changes:
 *   app.billing.limits.FREE.CODING_RUN=20
 *   app.billing.pro.monthly-amount-paise=29900
 */
@Component
public class PlanCatalog {

    private static final Map<FeatureKey, PlanDefinition.Limit> FREE_LIMITS = limits(
        FeatureKey.AI_TEXT_INTERVIEW, 5, UsagePeriod.DAILY,
        FeatureKey.AI_VOICE_INTERVIEW, 2, UsagePeriod.DAILY,
        FeatureKey.AI_COACH_MESSAGE, 20, UsagePeriod.DAILY,
        FeatureKey.RESUME_ATS_ANALYSIS, 3, UsagePeriod.MONTHLY,
        FeatureKey.RESUME_JOB_MATCH, 3, UsagePeriod.MONTHLY,
        FeatureKey.RESUME_AI_IMPROVEMENT, 2, UsagePeriod.MONTHLY,
        FeatureKey.RESUME_INTERVIEW_PREP, 2, UsagePeriod.MONTHLY,
        FeatureKey.CODING_RUN, 20, UsagePeriod.DAILY,
        FeatureKey.CODING_SUBMIT, 10, UsagePeriod.DAILY,
        FeatureKey.SQL_RUN, 30, UsagePeriod.DAILY,
        FeatureKey.SQL_SUBMIT, 15, UsagePeriod.DAILY);

    private static final Map<FeatureKey, PlanDefinition.Limit> PRO_LIMITS = limits(
        FeatureKey.AI_TEXT_INTERVIEW, 50, UsagePeriod.DAILY,
        FeatureKey.AI_VOICE_INTERVIEW, 20, UsagePeriod.DAILY,
        FeatureKey.AI_COACH_MESSAGE, 200, UsagePeriod.DAILY,
        FeatureKey.RESUME_ATS_ANALYSIS, 50, UsagePeriod.MONTHLY,
        FeatureKey.RESUME_JOB_MATCH, 50, UsagePeriod.MONTHLY,
        FeatureKey.RESUME_AI_IMPROVEMENT, 30, UsagePeriod.MONTHLY,
        FeatureKey.RESUME_INTERVIEW_PREP, 30, UsagePeriod.MONTHLY,
        FeatureKey.CODING_RUN, 100, UsagePeriod.DAILY,
        FeatureKey.CODING_SUBMIT, 50, UsagePeriod.DAILY,
        FeatureKey.SQL_RUN, 150, UsagePeriod.DAILY,
        FeatureKey.SQL_SUBMIT, 75, UsagePeriod.DAILY);

    private static final Map<FeatureKey, String> FREE_ACCESS = Map.of(
        FeatureKey.PRACTICE_MODULES, "AVAILABLE",
        FeatureKey.PERFORMANCE_ANALYTICS, "BASIC",
        FeatureKey.SKILL_GAP, "AVAILABLE",
        FeatureKey.MY_PLAN, "AVAILABLE");

    private static final Map<FeatureKey, String> PRO_ACCESS = Map.of(
        FeatureKey.PRACTICE_MODULES, "AVAILABLE",
        FeatureKey.PERFORMANCE_ANALYTICS, "FULL",
        FeatureKey.SKILL_GAP, "FULL",
        FeatureKey.MY_PLAN, "FULL");

    private final Map<Plan, PlanDefinition> definitions = new EnumMap<>(Plan.class);
    private final Map<BillingCycle, Long> proPricesPaise = new EnumMap<>(BillingCycle.class);
    private final String currency;

    public PlanCatalog(
        Environment environment,
        @Value("${app.billing.pro.monthly-amount-paise:29900}") long monthlyPaise,
        @Value("${app.billing.pro.annual-amount-paise:249900}") long annualPaise,
        @Value("${app.billing.razorpay.currency:INR}") String currency
    ) {
        definitions.put(Plan.FREE, new PlanDefinition(Plan.FREE, withOverrides(environment, Plan.FREE, FREE_LIMITS), FREE_ACCESS));
        definitions.put(Plan.PRO, new PlanDefinition(Plan.PRO, withOverrides(environment, Plan.PRO, PRO_LIMITS), PRO_ACCESS));
        if (monthlyPaise <= 0 || annualPaise <= 0) {
            throw new IllegalStateException("PRO prices must be positive amounts in paise");
        }
        proPricesPaise.put(BillingCycle.MONTHLY, monthlyPaise);
        proPricesPaise.put(BillingCycle.ANNUAL, annualPaise);
        this.currency = currency == null || currency.isBlank() ? "INR" : currency.trim().toUpperCase();
    }

    /** FREE and PRO are sold; any other stored plan value is treated like FREE. */
    public PlanDefinition definition(Plan plan) {
        return definitions.getOrDefault(plan, definitions.get(Plan.FREE));
    }

    public Map<Plan, PlanDefinition> all() {
        return Map.copyOf(definitions);
    }

    /** Server-side price of PRO for a billing cycle, in the smallest currency unit (paise). */
    public long proPricePaise(BillingCycle cycle) {
        return proPricesPaise.get(cycle);
    }

    public Map<BillingCycle, Long> proPrices() {
        return Map.copyOf(proPricesPaise);
    }

    public String currency() {
        return currency;
    }

    private static Map<FeatureKey, PlanDefinition.Limit> withOverrides(Environment environment, Plan plan, Map<FeatureKey, PlanDefinition.Limit> defaults) {
        Map<FeatureKey, PlanDefinition.Limit> result = new EnumMap<>(FeatureKey.class);
        defaults.forEach((feature, limit) -> {
            Integer override = environment.getProperty("app.billing.limits." + plan.name() + "." + feature.name(), Integer.class);
            result.put(feature, override == null ? limit : new PlanDefinition.Limit(Math.max(0, override), limit.period()));
        });
        return Map.copyOf(result);
    }

    private static Map<FeatureKey, PlanDefinition.Limit> limits(Object... values) {
        Map<FeatureKey, PlanDefinition.Limit> map = new EnumMap<>(FeatureKey.class);
        for (int i = 0; i < values.length; i += 3) {
            map.put((FeatureKey) values[i], new PlanDefinition.Limit((Integer) values[i + 1], (UsagePeriod) values[i + 2]));
        }
        return Map.copyOf(map);
    }
}
