package com.careerai.backend.billing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Time source for usage periods and subscription periods (replaceable in tests).
 * Daily/monthly usage windows follow the calendar in app.billing.usage-zone (default Asia/Kolkata);
 * subscription instants are stored as UTC Instants.
 */
@Configuration
public class BillingClockConfig {

    @Bean
    public Clock billingClock() {
        return Clock.systemUTC();
    }

    @Bean
    public ZoneId billingZone(@Value("${app.billing.usage-zone:Asia/Kolkata}") String zone) {
        return ZoneId.of(zone);
    }
}
