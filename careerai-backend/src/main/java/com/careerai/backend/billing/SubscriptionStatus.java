package com.careerai.backend.billing;

/**
 * Lifecycle of one purchase. Transitions only move forward:
 *   PENDING → ACTIVE (verified payment) | FAILED (payment failed) | CANCELLED (order not created)
 *   FAILED  → ACTIVE (a later capture of the same order was confirmed)
 *   ACTIVE  → EXPIRED (access period over)
 * ACTIVE never goes back to PENDING/FAILED because of a late or duplicate event.
 */
public enum SubscriptionStatus {
    PENDING,
    ACTIVE,
    EXPIRED,
    CANCELLED,
    FAILED
}
