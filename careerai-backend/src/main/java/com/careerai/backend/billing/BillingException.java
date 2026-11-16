package com.careerai.backend.billing;

import org.springframework.http.HttpStatus;

/**
 * A billing request that cannot be completed, with a safe message and a code such as
 * PAYMENT_GATEWAY_UNAVAILABLE, PAYMENT_VERIFICATION_FAILED, PAYMENT_FAILED, PAYMENT_PENDING,
 * ORDER_NOT_FOUND, WEBHOOK_SIGNATURE_INVALID. Never carries keys, secrets or gateway internals.
 */
public class BillingException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public BillingException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static BillingException gatewayUnavailable() {
        return new BillingException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_GATEWAY_UNAVAILABLE", "Payments are temporarily unavailable. Please try again later.");
    }
}
