package com.slotsure.slot.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotsure.slot.dto.HoldRequest;
import com.slotsure.slot.dto.HoldResponse;
import com.slotsure.slot.dto.SlotResponse;
import com.slotsure.slot.entity.Reservation;
import com.slotsure.slot.entity.ReservationStatus;
import com.slotsure.slot.event.EventEnvelope;
import com.slotsure.slot.event.SlotConfirmPayload;
import com.slotsure.slot.event.SlotReleasePayload;
import com.slotsure.slot.repository.ReservationRepository;
import com.slotsure.slot.repository.SlotRepository;
import com.slotsure.slot.service.HoldService;
import com.slotsure.slot.service.SlotCounterService;
import com.slotsure.slot.service.SlotService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/** Sends real slot.confirm / slot.release messages through Kafka. Run with: mvn verify -Dgroups=integration */
@Tag("integration")
@SpringBootTest(properties = "slotsure.hold.sweep-interval-ms=3600000")
class SlotEventsIntegrationTest {

    @Autowired
    private SlotService slotService;
    @Autowired
    private HoldService holdService;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private SlotRepository slotRepository;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void confirmIsIdempotentAndReleaseCompensates() throws Exception {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        LocalDate date = LocalDate.of(2100, 1, 1).plusDays(random.nextInt(0, 30_000));
        List<SlotResponse> slots = slotService.findSlots(random.nextDouble(-60, 60), random.nextDouble(-170, 170), date);
        String slotId = slots.get(0).slotId();
        HoldResponse hold = holdService.createHold(UUID.randomUUID().toString(), new HoldRequest(slotId, "cust-kafka"));
        assertThat(redis.opsForValue().get(SlotCounterService.key(slotId))).isEqualTo("9");
        String orderId = UUID.randomUUID().toString();

        // The same confirm event delivered twice must only book the slot once.
        String confirm = envelope("slot.confirm", orderId, new SlotConfirmPayload(orderId, hold.holdId()));
        kafkaTemplate.send("slot.confirm", orderId, confirm).get();
        kafkaTemplate.send("slot.confirm", orderId, confirm).get();

        Reservation confirmed = waitFor(hold.holdId(), r -> r.getStatus() == ReservationStatus.CONFIRMED);
        assertThat(confirmed.getOrderId()).isEqualTo(orderId);
        Thread.sleep(2_000); // give the duplicate time to be processed (and skipped)
        assertThat(slotRepository.findById(slotId).orElseThrow().getBooked()).isEqualTo(1);

        String release = envelope("slot.release", orderId, new SlotReleasePayload(orderId, hold.holdId(), "REFUND"));
        kafkaTemplate.send("slot.release", orderId, release).get();

        waitFor(hold.holdId(), r -> r.getStatus() == ReservationStatus.RELEASED);
        assertThat(slotRepository.findById(slotId).orElseThrow().getBooked()).isZero();
        assertThat(redis.opsForValue().get(SlotCounterService.key(slotId))).isEqualTo("10");
    }

    private String envelope(String type, String orderId, Object payload) throws Exception {
        return objectMapper.writeValueAsString(EventEnvelope.create(type, orderId, objectMapper.valueToTree(payload)));
    }

    private Reservation waitFor(String holdId, Predicate<Reservation> condition) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        while (Instant.now().isBefore(deadline)) {
            Reservation reservation = reservationRepository.findById(holdId).orElseThrow();
            if (condition.test(reservation)) {
                return reservation;
            }
            Thread.sleep(250);
        }
        throw new AssertionError("Hold " + holdId + " did not reach the expected state in 30 s");
    }
}
