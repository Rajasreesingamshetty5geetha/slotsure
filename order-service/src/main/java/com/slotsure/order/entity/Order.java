package com.slotsure.order.entity;

import com.slotsure.order.exception.IllegalTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
public class Order {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 64)
    private String customerId;

    @Column(nullable = false, length = 36)
    private String holdId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrderStatus status;

    @Column(nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Order() {
    }

    public Order(String customerId, String holdId, BigDecimal amount, String idempotencyKey) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID().toString();
        this.customerId = customerId;
        this.holdId = holdId;
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        this.status = OrderStatus.PENDING;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void moveTo(OrderStatus next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalTransitionException("Order " + id + " cannot move from " + status + " to " + next);
        }
        this.status = next;
        this.updatedAt = Instant.now();
    }
}
