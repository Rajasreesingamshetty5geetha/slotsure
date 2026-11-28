package com.slotsure.dispatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One order given to one agent. orderId is the primary key, so an order is assigned at most once.
 * The status is stored as VARCHAR, not MySQL ENUM, so new values never need a column change.
 */
@Entity
@Table(name = "assignments", indexes = {
        @Index(name = "ix_assignment_date_hour", columnList = "slot_date, start_hour"),
        @Index(name = "ix_assignment_agent_date", columnList = "agent_id, slot_date")
})
@Getter
public class Assignment {

    @Id
    @Column(name = "order_id", length = 36)
    private String orderId;

    @Column(name = "agent_id", nullable = false, length = 36)
    private String agentId;

    @Column(name = "area_name", length = 64)
    private String areaName;

    @Column(name = "slot_date", nullable = false)
    private LocalDate date;

    @Column(name = "start_hour", nullable = false)
    private int startHour;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private AssignmentStatus status;

    @Setter
    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    /** True for seeded dummy rows, so they can be wiped. */
    @Setter
    @Column(nullable = false)
    private boolean demo;

    protected Assignment() {
    }

    public Assignment(String orderId, String agentId, String areaName, LocalDate date, int startHour, Instant assignedAt) {
        this.orderId = orderId;
        this.agentId = agentId;
        this.areaName = areaName;
        this.date = date;
        this.startHour = startHour;
        this.status = AssignmentStatus.ASSIGNED;
        this.assignedAt = assignedAt;
    }

    public void markDelivered(Instant at) {
        this.status = AssignmentStatus.DELIVERED;
        this.deliveredAt = at;
    }

    public boolean isDelivered() {
        return status == AssignmentStatus.DELIVERED;
    }
}
