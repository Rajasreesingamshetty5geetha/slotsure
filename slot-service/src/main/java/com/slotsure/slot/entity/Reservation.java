package com.slotsure.slot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservations",
        uniqueConstraints = @UniqueConstraint(name = "uk_reservation_idempotency_key", columnNames = "idempotency_key"),
        indexes = {
                @Index(name = "ix_reservation_status_expires", columnList = "status, expires_at"),
                @Index(name = "ix_reservation_slot", columnList = "slot_id")
        })
@Getter
@Setter
public class Reservation {

    /** The holdId returned to clients. */
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "slot_id", nullable = false, length = 36)
    private String slotId;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReservationStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "order_id", length = 36)
    private String orderId;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** True for seeded dummy rows, so they can be wiped. */
    @Column(nullable = false)
    private boolean demo;

    protected Reservation() {
    }

    public Reservation(String slotId, String customerId, String idempotencyKey, Instant createdAt, Instant expiresAt) {
        this.id = UUID.randomUUID().toString();
        this.slotId = slotId;
        this.customerId = customerId;
        this.idempotencyKey = idempotencyKey;
        this.status = ReservationStatus.HELD;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }
}
