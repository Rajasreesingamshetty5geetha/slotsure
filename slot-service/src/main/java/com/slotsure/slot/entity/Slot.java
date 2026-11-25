package com.slotsure.slot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
                columnNames = {"h3_cell", "slot_date", "start_hour"}))
@Getter
@Setter
public class Slot {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "h3_cell", nullable = false, length = 16)
    private String h3Cell;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "start_hour", nullable = false)
    private int startHour;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private int booked;

    @Version
    private Long version;

    protected Slot() {
    }

    public Slot(String h3Cell, LocalDate slotDate, int startHour, int capacity) {
        this.id = UUID.randomUUID().toString();
        this.h3Cell = h3Cell;
        this.slotDate = slotDate;
        this.startHour = startHour;
        this.capacity = capacity;
        this.booked = 0;
    }
}
