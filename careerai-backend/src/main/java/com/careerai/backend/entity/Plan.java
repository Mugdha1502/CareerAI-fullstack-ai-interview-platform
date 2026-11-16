package com.careerai.backend.entity;

/**
 * Subscription plans. Matches the frontend plan codes; GUEST is a
 * frontend-only session type and is never stored for a registered user.
 */
public enum Plan {
    FREE,
    PRO,
    PREMIUM
}
