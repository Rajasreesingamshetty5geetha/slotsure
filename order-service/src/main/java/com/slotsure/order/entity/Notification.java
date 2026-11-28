package com.slotsure.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "notifications", indexes = @Index(name = "ix_notification_customer_created",
        columnList = "customer_id, created_at"))
@Getter
public class Notification {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Column(name = "order_id", nullable = false, length = 36)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 24)
    private NotificationType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 512)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** "read" is a reserved word in MySQL. */
    @Column(name = "is_read", nullable = false)
    private boolean read;

    protected Notification() {
    }

    public Notification(String customerId, String orderId, NotificationType type, String title, String body) {
        this.id = UUID.randomUUID().toString();
        this.customerId = customerId;
        this.orderId = orderId;
        this.type = type;
        this.title = title;
        this.body = body;
        // Millisecond precision so the value a client sends back as "after" matches the stored one exactly.
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    public void markRead() {
        this.read = true;
    }
}
