package com.slotsure.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One money movement attempt for one order: a gateway call, the checkout capture of a PREPAID order,
 * or the cash taken on delivery. Every attempt is stored, successful or not.
 * The status is stored as VARCHAR, not MySQL ENUM, so new values never need a column change.
 */
@Entity
@Table(name = "payment_attempts", indexes = {
        @Index(name = "idx_payment_attempts_order", columnList = "order_id"),
        @Index(name = "idx_payment_attempts_created", columnList = "created_at")
})
@Getter
@Setter
@Builder
public class PaymentAttempt {

    public static final String CHECKOUT = "CHECKOUT";
    public static final String COD = "COD";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;

    @Column(name = "order_id", nullable = false, length = 36)
    private String orderId;

    @Column(name = "customer_id", nullable = false, length = 36)
    private String customerId;

    @Column(nullable = false, length = 20)
    private String gateway;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private PaymentStatus status;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** True for seeded dummy rows, so they can be wiped. */
    @Column(nullable = false)
    private boolean demo;

    protected PaymentAttempt() {
    }

    private PaymentAttempt(String id, String orderId, String customerId, String gateway, BigDecimal amount,
                           PaymentStatus status, String failureReason, Instant createdAt, boolean demo) {
        this.id = id;
        this.orderId = orderId;
        this.customerId = customerId;
        this.gateway = gateway;
        this.amount = amount;
        this.status = status;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
        this.demo = demo;
    }
}
