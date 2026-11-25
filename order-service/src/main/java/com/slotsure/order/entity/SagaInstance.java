package com.slotsure.order.entity;

import com.slotsure.order.exception.IllegalTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;

@Entity
@Table(name = "saga_instances")
@Getter
public class SagaInstance {

    @Id
    @Column(length = 36)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private SagaStep step;

    @Column(length = 512)
    private String lastError;

    @Column(nullable = false)
    private Instant updatedAt;

    protected SagaInstance() {
    }

    public SagaInstance(String orderId) {
        this.orderId = orderId;
        this.step = SagaStep.PAYMENT_REQUESTED;
        this.updatedAt = Instant.now();
    }

    public void moveTo(SagaStep next) {
        if (!step.canMoveTo(next)) {
            throw new IllegalTransitionException("Saga " + orderId + " cannot move from " + step + " to " + next);
        }
        this.step = next;
        this.updatedAt = Instant.now();
    }

    public void recordError(String error) {
        this.lastError = error;
        this.updatedAt = Instant.now();
    }
}
