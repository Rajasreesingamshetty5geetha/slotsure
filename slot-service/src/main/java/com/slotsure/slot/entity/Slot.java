package com.slotsure.slot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "slots",
        uniqueConstraints = @UniqueConstraint(name = "uk_slot_cell_date_hour",
                columnNames = {"h3_cell", "slot_date", "start_hour"}),
        indexes = @Index(name = "ix_slot_date_area", columnList = "slot_date, area_name"))
@Getter
@Setter
public class Slot {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "h3_cell", nullable = false, length = 16)
    private String h3Cell;

    @Column(name = "area_name", length = 64)
    private String areaName;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "start_hour", nullable = false)
    private int startHour;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private int booked;

    /** True for seeded dummy rows, so they can be wiped. */
    @Column(nullable = false)
    private boolean demo;

    @Version
    private Long version;

    protected Slot() {
    }

    public Slot(String h3Cell, String areaName, LocalDate slotDate, int startHour, int capacity) {
        this.id = UUID.randomUUID().toString();
        this.h3Cell = h3Cell;
        this.areaName = areaName;
        this.slotDate = slotDate;
        this.startHour = startHour;
        this.capacity = capacity;
        this.booked = 0;
    }

    /** Slots are one hour long. */
    public int getEndHour() {
        return startHour + 1;
    }
}
