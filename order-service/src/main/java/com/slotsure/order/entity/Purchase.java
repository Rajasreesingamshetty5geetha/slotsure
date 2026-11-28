package com.slotsure.order.entity;

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

/** Something the customer bought at checkout and now needs a delivery slot for. */
@Entity
@Table(name = "purchases", indexes = @Index(name = "ix_purchase_customer", columnList = "customer_id, placed_at"))
@Getter
public class Purchase {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Column(name = "item_name", nullable = false, length = 128)
    private String itemName;

    @Column(nullable = false, length = 64)
    private String category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "payment_mode", nullable = false, length = 16)
    private PaymentMode paymentMode;

    @Column(name = "placed_at", nullable = false)
    private Instant placedAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private PurchaseStatus status;

    /** The latest order that is booking or has booked this purchase; cleared when that order fails. */
    @Column(name = "order_id", length = 36)
    private String orderId;

    @Setter
    @Column(nullable = false)
    private boolean demo;

    /** Booking and delivery events can touch the same purchase at once; the loser retries. */
    @Version
    private Long version;

    protected Purchase() {
    }

    public Purchase(String id, String customerId, String itemName, String category, BigDecimal amount,
                    PaymentMode paymentMode, Instant placedAt) {
        this.id = id;
        this.customerId = customerId;
        this.itemName = itemName;
        this.category = category;
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        this.paymentMode = paymentMode;
        this.placedAt = placedAt;
        this.status = PurchaseStatus.AWAITING_SLOT;
    }

    public void attachOrder(String orderId) {
        this.orderId = orderId;
    }

    /** Frees the purchase for another try, unless a newer order has taken it since. */
    public void detachOrder(String orderId) {
        if (orderId.equals(this.orderId) && status == PurchaseStatus.AWAITING_SLOT) {
            this.orderId = null;
        }
    }

    public void markScheduled() {
        if (status == PurchaseStatus.AWAITING_SLOT) {
            this.status = PurchaseStatus.SCHEDULED;
        }
    }

    public void markDelivered() {
        this.status = PurchaseStatus.DELIVERED;
    }
}
