package com.slotsure.slot.integration;

import com.slotsure.slot.dto.HoldRequest;
import com.slotsure.slot.dto.HoldResponse;
import com.slotsure.slot.dto.SlotResponse;
import com.slotsure.slot.entity.ReservationStatus;
import com.slotsure.slot.exception.ApiException;
import com.slotsure.slot.repository.ReservationRepository;
import com.slotsure.slot.service.HoldService;
import com.slotsure.slot.service.HoldSweeper;
import com.slotsure.slot.service.SlotCounterService;
import com.slotsure.slot.service.SlotService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against the Docker infrastructure from CONTRACT section 3 (MySQL 3307, Redis 6380, Kafka 9092).
 * Run with: mvn verify -Dgroups=integration
 */
@Tag("integration")
@SpringBootTest(properties = {
        "slotsure.hold.expiry-seconds=2",
        // Keep the scheduled sweeper out of the way; the expiry test triggers it by hand.
        "slotsure.hold.sweep-interval-ms=3600000"
})
class HoldFlowIntegrationTest {

    @Autowired
    private SlotService slotService;
    @Autowired
    private HoldService holdService;
    @Autowired
    private HoldSweeper holdSweeper;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private StringRedisTemplate redis;

    @Test
    void fiveHundredConcurrentHoldsOnThreePlacesLetExactlyThreeThrough() throws Exception {
        String slotId = freshSlotWithCounter(3);
        int requests = 500;
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger slotFull = new AtomicInteger();
        AtomicInteger otherErrors = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < requests; i++) {
                String customer = "cust-" + i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        holdService.createHold(UUID.randomUUID().toString(), new HoldRequest(slotId, customer));
                        succeeded.incrementAndGet();
                    } catch (ApiException e) {
                        if ("SLOT_FULL".equals(e.getCode())) {
                            slotFull.incrementAndGet();
                        } else {
                            otherErrors.incrementAndGet();
                        }
                    } catch (Exception e) {
                        otherErrors.incrementAndGet();
                    }
                    return null;
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(succeeded.get()).isEqualTo(3);
        assertThat(slotFull.get()).isEqualTo(497);
        assertThat(otherErrors.get()).isZero();
        assertThat(redis.opsForValue().get(SlotCounterService.key(slotId))).isEqualTo("0");
    }

    @Test
    void sameIdempotencyKeyTwiceReturnsSameHoldAndDecrementsOnce() {
        String slotId = freshSlotWithCounter(5);
        String key = UUID.randomUUID().toString();
        HoldRequest request = new HoldRequest(slotId, "cust-idem");

        HoldResponse first = holdService.createHold(key, request);
        HoldResponse second = holdService.createHold(key, request);

        assertThat(second.holdId()).isEqualTo(first.holdId());
        assertThat(second.expiresAt()).isEqualTo(first.expiresAt());
        assertThat(redis.opsForValue().get(SlotCounterService.key(slotId))).isEqualTo("4");
    }

    @Test
    void expiredHoldIsReleasedBySweeperAndPlaceReturns() throws Exception {
        String slotId = freshSlotWithCounter(2);
        HoldResponse hold = holdService.createHold(UUID.randomUUID().toString(), new HoldRequest(slotId, "cust-exp"));
        assertThat(redis.opsForValue().get(SlotCounterService.key(slotId))).isEqualTo("1");

        Thread.sleep(2_500);
        holdSweeper.releaseExpiredHolds();

        assertThat(reservationRepository.findById(hold.holdId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.RELEASED);
        assertThat(holdService.getHold(hold.holdId()).status()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(redis.opsForValue().get(SlotCounterService.key(slotId))).isEqualTo("2");
    }

    /** Creates slots for a random cell and far-future date, then forces the first slot's counter. */
    private String freshSlotWithCounter(int remaining) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double lat = random.nextDouble(-60, 60);
        double lng = random.nextDouble(-170, 170);
        LocalDate date = LocalDate.of(2100, 1, 1).plusDays(random.nextInt(0, 30_000));
        List<SlotResponse> slots = slotService.findSlots(lat, lng, date);
        assertThat(slots).hasSize(14);
        String slotId = slots.get(0).slotId();
        redis.opsForValue().set(SlotCounterService.key(slotId), Integer.toString(remaining));
        return slotId;
    }
}
