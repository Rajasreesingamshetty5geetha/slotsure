package com.slotsure.order.entity;

import com.slotsure.order.exception.IllegalTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Enums are stored as VARCHAR, not MySQL ENUM, so new values never need a column change.
 * Delivery details are nullable because orders from before V2 do not have them.
 */
@Entity
@Table(name = "orders", indexes = {
        @Index(name = "ix_order_customer_created", columnList = "customer_id, created_at"),
        @Index(name = "ix_order_slot_date", columnList = "slot_date"),
        @Index(name = "ix_order_purchase", columnList = "purchase_id")
})
@Getter
public class Order {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Column(name = "hold_id", nullable = false, length = 36)
    private String holdId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 24)
    private OrderStatus status;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Setter
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Setter
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Setter
    @Column(name = "purchase_id", length = 36)
    private String purchaseId;

    @Setter
    @Column(name = "item_name", length = 128)
    private String itemName;

    @Setter
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "payment_mode", length = 16)
    private PaymentMode paymentMode;

    @Setter
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "payment_choice", length = 16)
    private PaymentChoice paymentChoice;

    @Setter
    private Double lat;

    @Setter
    private Double lng;

    @Setter
    @Column(length = 512)
    private String address;

    @Setter
    @Column(name = "area_name", length = 64)
    private String areaName;

    @Setter
    @Column(name = "slot_date")
    private LocalDate slotDate;

    @Setter
    @Column(name = "start_hour")
    private Integer startHour;

    @Setter
    @Column(name = "end_hour")
    private Integer endHour;

    @Column(name = "agent_id", length = 36)
    private String agentId;

    @Column(name = "agent_name", length = 128)
    private String agentName;

    @Column(name = "agent_phone", length = 32)
    private String agentPhone;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    /** True for seeded dummy rows, so they can be wiped. */
    @Setter
    @Column(nullable = false)
    private boolean demo;

    /**
     * slot.confirmed and delivery.assigned arrive on different topics and can be handled at the same
     * moment; without this, the later commit would write back the status the earlier one replaced.
     * The loser fails with an optimistic-lock error and the Kafka retry re-reads the order.
     */
    @Version
    private Long version;

    protected Order() {
    }

    public Order(String customerId, String holdId, BigDecimal amount, String idempotencyKey) {
        this(UUID.randomUUID().toString(), customerId, holdId, amount, idempotencyKey);
    }

    public Order(String id, String customerId, String holdId, BigDecimal amount, String idempotencyKey) {
        Instant now = Instant.now();
        this.id = id;
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

    public void assignAgent(String agentId, String agentName, String agentPhone) {
        this.agentId = agentId;
        this.agentName = agentName;
        this.agentPhone = agentPhone;
        this.updatedAt = Instant.now();
    }

    public boolean hasAgent() {
        return agentName != null;
    }

    public void markDelivered(Instant deliveredAt) {
        moveTo(OrderStatus.DELIVERED);
        this.deliveredAt = deliveredAt;
    }

    public PaymentChoice effectivePaymentChoice() {
        return PaymentChoice.orDefault(paymentChoice);
    }
}
