package com.slotsure.slot.integration;

import com.slotsure.slot.dto.AdminSlotSummary;
import com.slotsure.slot.dto.SlotResponse;
import com.slotsure.slot.entity.Slot;
import com.slotsure.slot.repository.SlotRepository;
import com.slotsure.slot.service.SlotCounterService;
import com.slotsure.slot.service.SlotService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the admin GROUP BY queries, the area backfill and the demo seeding against real MySQL and Redis.
 * Run with: mvn verify -Dgroups=integration
 */
@Tag("integration")
@SpringBootTest(properties = "slotsure.hold.sweep-interval-ms=3600000")
class AdminSlotsIntegrationTest {

    @Autowired
    private SlotService slotService;
    @Autowired
    private SlotRepository slotRepository;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void adminSummaryAggregatesByAreaAndHourInMySql() {
        LocalDate date = LocalDate.of(2100, 1, 1).plusDays(ThreadLocalRandom.current().nextInt(0, 30_000));
        List<SlotResponse> koramangala = slotService.findSlots(12.9352, 77.6245, date);
        List<SlotResponse> hebbal = slotService.findSlots(13.0358, 77.5970, date);
        assertThat(koramangala).allSatisfy(slot -> assertThat(slot.areaName()).isEqualTo("Koramangala"));
        assertThat(hebbal).allSatisfy(slot -> assertThat(slot.areaName()).isEqualTo("Hebbal"));

        book(koramangala, 19, 6);
        book(koramangala, 18, 4);
        book(hebbal, 20, 3);

        AdminSlotSummary summary = slotService.adminSummary(date);

        assertThat(summary.totalCapacity()).isEqualTo(280);
        assertThat(summary.totalBooked()).isEqualTo(13);
        assertThat(summary.utilisationPct()).isEqualTo(4.6);
        assertThat(summary.byArea()).containsExactly(
                new AdminSlotSummary.AreaLoad("Koramangala", 140, 10),
                new AdminSlotSummary.AreaLoad("Hebbal", 140, 3));
        assertThat(summary.busiest()).containsExactly(
                new AdminSlotSummary.BusySlot("Koramangala", 19, 20, 6, 10),
                new AdminSlotSummary.BusySlot("Koramangala", 18, 19, 4, 10),
                new AdminSlotSummary.BusySlot("Hebbal", 20, 21, 3, 10));
    }

    @Test
    void startupNamedEverySlotAndSeededTodayAndYesterday() {
        assertThat(slotRepository.findCellsWithoutAreaName()).isEmpty();

        LocalDate today = slotService.today();
        for (LocalDate date : List.of(today.minusDays(1), today)) {
            assertThat(slotRepository.existsBySlotDateAndDemoTrue(date)).as("demo slots on %s", date).isTrue();
            AdminSlotSummary summary = slotService.adminSummary(date);
            assertThat(summary.totalBooked()).isPositive();
            assertThat(summary.byArea()).isNotEmpty();
            assertThat(summary.busiest()).isNotEmpty().hasSizeLessThanOrEqualTo(5);
        }

        // Booked places on demo slots are CONFIRMED reservations, and Redis agrees with MySQL.
        List<Map<String, Object>> demoSlots = jdbcTemplate.queryForList("""
                select s.id, s.capacity, s.booked,
                       (select count(*) from reservations r where r.slot_id = s.id and r.status = 'CONFIRMED') confirmed,
                       (select count(*) from reservations r where r.slot_id = s.id and r.status = 'HELD') held
                from slots s where s.demo = true and s.slot_date = ? and s.booked > 0 limit 20
                """, today.minusDays(1));
        assertThat(demoSlots).isNotEmpty();
        for (Map<String, Object> row : demoSlots) {
            long capacity = ((Number) row.get("capacity")).longValue();
            long booked = ((Number) row.get("booked")).longValue();
            assertThat(((Number) row.get("confirmed")).longValue()).isEqualTo(booked);
            String counter = redis.opsForValue().get(SlotCounterService.key((String) row.get("id")));
            long held = ((Number) row.get("held")).longValue();
            assertThat(counter).isEqualTo(Long.toString(capacity - booked - held));
        }
    }

    private void book(List<SlotResponse> slots, int startHour, int booked) {
        SlotResponse response = slots.stream().filter(slot -> slot.startHour() == startHour).findFirst().orElseThrow();
        Slot slot = slotRepository.findById(response.slotId()).orElseThrow();
        slot.setBooked(booked);
        slotRepository.save(slot);
    }
}
