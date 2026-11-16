package com.careerai.backend.billing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Housekeeping only: marks ACTIVE purchases whose period ended as EXPIRED (for history/admin
 * queries). Entitlement never depends on it — every check compares expiresAt with "now".
 * Disable with app.billing.expiry-job.enabled=false.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.billing.expiry-job.enabled", havingValue = "true", matchIfMissing = true)
public class SubscriptionExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionExpiryJob.class);

    private final SubscriptionActivationService activation;

    public SubscriptionExpiryJob(SubscriptionActivationService activation) {
        this.activation = activation;
    }

    @Scheduled(initialDelayString = "${app.billing.expiry-job.initial-delay-ms:60000}", fixedDelayString = "${app.billing.expiry-job.interval-ms:3600000}")
    public void expireEndedPeriods() {
        int expired = activation.expireEndedPeriods();
        if (expired > 0) log.info("SUBSCRIPTIONS_EXPIRED: {} PRO period(s) ended.", expired);
    }
}
