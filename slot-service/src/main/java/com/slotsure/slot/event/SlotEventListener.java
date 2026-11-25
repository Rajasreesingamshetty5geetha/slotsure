package com.slotsure.slot.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotsure.slot.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SlotEventListener {

    private final ObjectMapper objectMapper;
    private final ReservationService reservationService;

    @KafkaListener(topics = "${slotsure.topics.slot-confirm:slot.confirm}")
    public void onSlotConfirm(ConsumerRecord<String, String> record) throws JsonProcessingException {
        EventEnvelope envelope = readEnvelope(record);
        SlotConfirmPayload payload = objectMapper.treeToValue(envelope.payload(), SlotConfirmPayload.class);
        requireIds(payload.orderId(), payload.holdId(), envelope);
        log.info("Received slot.confirm eventId={} orderId={} holdId={}", envelope.eventId(), payload.orderId(), payload.holdId());
        withOneRetry(payload.orderId(), () -> reservationService.confirm(envelope.eventId(), payload));
    }

    @KafkaListener(topics = "${slotsure.topics.slot-release:slot.release}")
    public void onSlotRelease(ConsumerRecord<String, String> record) throws JsonProcessingException {
        EventEnvelope envelope = readEnvelope(record);
        SlotReleasePayload payload = objectMapper.treeToValue(envelope.payload(), SlotReleasePayload.class);
        requireIds(payload.orderId(), payload.holdId(), envelope);
        log.info("Received slot.release eventId={} orderId={} holdId={}", envelope.eventId(), payload.orderId(), payload.holdId());
        withOneRetry(payload.orderId(), () -> reservationService.release(envelope.eventId(), payload));
    }

    private EventEnvelope readEnvelope(ConsumerRecord<String, String> record) throws JsonProcessingException {
        EventEnvelope envelope = objectMapper.readValue(record.value(), EventEnvelope.class);
        if (envelope.eventId() == null || envelope.payload() == null) {
            throw new IllegalArgumentException("Envelope on " + record.topic() + " has no eventId or payload");
        }
        return envelope;
    }

    private static void requireIds(String orderId, String holdId, EventEnvelope envelope) {
        if (orderId == null || holdId == null) {
            throw new IllegalArgumentException("Event " + envelope.eventId() + " is missing orderId or holdId");
        }
    }

    /** A concurrent update (sweeper or another event) rolls the whole transaction back; try it once more. */
    private static void withOneRetry(String orderId, Runnable action) {
        try {
            action.run();
        } catch (OptimisticLockingFailureException e) {
            log.warn("Optimistic lock conflict for orderId {}, retrying once", orderId);
            action.run();
        }
    }
}
