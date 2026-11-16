package com.careerai.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One received Razorpay webhook (x-razorpay-event-id is unique). Inserting it first makes each
 * event processed at most once: a duplicate delivery hits the unique key and is acknowledged
 * without repeating the business action.
 */
@Entity
@Table(name = "payment_webhook_events", uniqueConstraints = @UniqueConstraint(name = "uk_payment_webhook_event", columnNames = "event_id"))
@Getter
@Setter
@NoArgsConstructor
public class PaymentWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 80)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "razorpay_order_id", length = 64)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 64)
    private String razorpayPaymentId;

    /** e.g. ACTIVATED, ALREADY_ACTIVE, MARKED_FAILED, IGNORED, UNKNOWN_ORDER, AMOUNT_MISMATCH */
    @Column(length = 40)
    private String outcome;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
}
