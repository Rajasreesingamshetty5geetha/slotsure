package com.slotsure.slot.repository;

import com.slotsure.slot.entity.Slot;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface SlotRepository extends JpaRepository<Slot, String> {

    List<Slot> findByH3CellAndSlotDateOrderByStartHour(String h3Cell, LocalDate slotDate);

    boolean existsBySlotDateAndDemoTrue(LocalDate slotDate);

    @Query("select distinct s.h3Cell from Slot s where s.areaName is null")
    List<String> findCellsWithoutAreaName();

    /** Bumps the version so a concurrent entity update cannot silently write the old null back. */
    @Modifying
    @Query("""
            update Slot s set s.areaName = :areaName, s.version = s.version + 1
            where s.h3Cell in :cells and s.areaName is null
            """)
    int setAreaName(@Param("cells") Collection<String> cells, @Param("areaName") String areaName);

    @Query("""
            select s.areaName as areaName, sum(s.capacity) as capacity, sum(s.booked) as booked
            from Slot s
            where s.slotDate = :date
            group by s.areaName
            order by sum(s.booked) desc, s.areaName
            """)
    List<AreaTotals> totalsByArea(@Param("date") LocalDate date);

    /** Area and hour pairs with at least one booking, busiest first. */
    @Query("""
            select s.areaName as areaName, s.startHour as startHour,
                   sum(s.capacity) as capacity, sum(s.booked) as booked
            from Slot s
            where s.slotDate = :date
            group by s.areaName, s.startHour
            having sum(s.booked) > 0
            order by sum(s.booked) desc, s.startHour, s.areaName
            """)
    List<HourTotals> busiestSlots(@Param("date") LocalDate date, Pageable pageable);

    interface AreaTotals {
        String getAreaName();

        long getCapacity();

        long getBooked();
    }

    interface HourTotals {
        String getAreaName();

        int getStartHour();

        long getCapacity();

        long getBooked();
    }
}
