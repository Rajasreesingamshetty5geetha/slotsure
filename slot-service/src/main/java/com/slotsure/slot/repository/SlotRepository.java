package com.slotsure.slot.repository;

import com.slotsure.slot.entity.Slot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SlotRepository extends JpaRepository<Slot, String> {

    List<Slot> findByH3CellAndSlotDateOrderByStartHour(String h3Cell, LocalDate slotDate);
}
