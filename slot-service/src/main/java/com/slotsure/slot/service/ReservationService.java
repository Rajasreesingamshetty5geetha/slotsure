package com.slotsure.slot.service;

import com.slotsure.slot.config.SlotsureProperties;
import com.slotsure.slot.entity.ProcessedEvent;
import com.slotsure.slot.entity.Reservation;
import com.slotsure.slot.entity.ReservationStatus;
import com.slotsure.slot.entity.Slot;
import com.slotsure.slot.event.SlotConfirmFailedPayload;
import com.slotsure.slot.event.SlotConfirmPayload;
import com.slotsure.slot.event.SlotConfirmedPayload;
import com.slotsure.slot.event.SlotReleasePayload;
import com.slotsure.slot.event.SlotReleasedPayload;
import com.slotsure.slot.repository.ProcessedEventRepository;
import com.slotsure.slot.repository.ReservationRepository;
import com.slotsure.slot.repository.SlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;

/**
 * Reservation state changes after a hold is created: expiry, confirmation and compensation.
 * Each public method is one transaction. Redis and Kafka side effects run only after the commit,
 * so a rolled-back change (for example an optimistic-lock conflict) never touches the counter.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final SlotRepository slotRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final SlotCounterService slotCounterService;
    private final EventPublisher eventPublisher;
    private final SlotsureProperties properties;

    /** Releases one expired hold. Returns true when it was released. */
    @Transactional
    public boolean expire(String holdId) {
        Reservation reservation = reservationRepository.findById(holdId).orElse(null);
        if (reservation == null
                || reservation.getStatus() != ReservationStatus.HELD
                || !reservation.getExpiresAt().isBefore(Instant.now())) {
            return false;
        }
        reservation.setStatus(ReservationStatus.RELEASED);
        log.info("Hold {} expired, status RELEASED", holdId);

        String slotId = reservation.getSlotId();
        afterCommit(() -> {
            slotCounterService.giveBack(slotId);
            eventPublisher.publish(properties.topics().slotReleased(), holdId, holdId,
                    new SlotReleasedPayload(holdId, slotId, SlotReleasedPayload.EXPIRED));
        });
        return true;
    }

    @Transactional
    public void confirm(String eventId, SlotConfirmPayload payload) {
        if (!markProcessed(eventId, payload.orderId())) {
            return;
        }
        String orderId = payload.orderId();
        String holdId = payload.holdId();
        Reservation reservation = reservationRepository.findById(holdId).orElse(null);
        if (reservation == null) {
            log.warn("Confirm failed for orderId {}: hold {} not found", orderId, holdId);
            publishConfirmFailed(orderId, holdId, SlotConfirmFailedPayload.HOLD_NOT_FOUND);
            return;
        }

        if (reservation.getStatus() == ReservationStatus.CONFIRMED && orderId.equals(reservation.getOrderId())) {
            log.info("Hold {} already CONFIRMED for orderId {}, re-sending slot.confirmed", holdId, orderId);
            Slot slot = slotRepository.findById(reservation.getSlotId()).orElseThrow();
            publishConfirmed(orderId, reservation, slot);
            return;
        }

        if (reservation.getStatus() != ReservationStatus.HELD || !reservation.getExpiresAt().isAfter(Instant.now())) {
            log.warn("Confirm failed for orderId {}: hold {} is {} and expired at {}",
                    orderId, holdId, reservation.getStatus(), reservation.getExpiresAt());
            publishConfirmFailed(orderId, holdId, SlotConfirmFailedPayload.HOLD_EXPIRED);
            return;
        }

        Slot slot = slotRepository.findById(reservation.getSlotId())
                .orElseThrow(() -> new IllegalStateException("Slot " + reservation.getSlotId() + " missing"));
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setOrderId(orderId);
        slot.setBooked(slot.getBooked() + 1);
        log.info("Hold {} CONFIRMED for orderId {} on slot {} (booked {})", holdId, orderId, slot.getId(), slot.getBooked());
        publishConfirmed(orderId, reservation, slot);
    }

    @Transactional
    public void release(String eventId, SlotReleasePayload payload) {
        if (!markProcessed(eventId, payload.orderId())) {
            return;
        }
        String orderId = payload.orderId();
        String holdId = payload.holdId();
        Reservation reservation = reservationRepository.findById(holdId).orElse(null);
        if (reservation == null) {
            log.warn("Release ignored for orderId {}: hold {} not found", orderId, holdId);
            return;
        }
        ReservationStatus previous = reservation.getStatus();
        if (previous == ReservationStatus.RELEASED) {
            log.info("Release ignored for orderId {}: hold {} already RELEASED", orderId, holdId);
            return;
        }

        reservation.setStatus(ReservationStatus.RELEASED);
        if (previous == ReservationStatus.CONFIRMED) {
            Slot slot = slotRepository.findById(reservation.getSlotId())
                    .orElseThrow(() -> new IllegalStateException("Slot " + reservation.getSlotId() + " missing"));
            slot.setBooked(Math.max(0, slot.getBooked() - 1));
        }
        log.info("Hold {} RELEASED from {} for orderId {} (reason {})", holdId, previous, orderId, payload.reason());

        String slotId = reservation.getSlotId();
        afterCommit(() -> {
            slotCounterService.giveBack(slotId);
            eventPublisher.publish(properties.topics().slotReleased(), holdId, orderId,
                    new SlotReleasedPayload(holdId, slotId, SlotReleasedPayload.COMPENSATION));
        });
    }

    /** Records the eventId in this transaction. Returns false when it was already processed. */
    private boolean markProcessed(String eventId, String orderId) {
        if (processedEventRepository.existsById(eventId)) {
            log.info("Skipping duplicate event {} for orderId {}", eventId, orderId);
            return false;
        }
        processedEventRepository.save(new ProcessedEvent(eventId, Instant.now()));
        return true;
    }

    private void publishConfirmed(String orderId, Reservation reservation, Slot slot) {
        SlotConfirmedPayload confirmed = new SlotConfirmedPayload(orderId, reservation.getId(), slot.getId(), slot.getH3Cell());
        afterCommit(() -> eventPublisher.publish(properties.topics().slotConfirmed(), orderId, orderId, confirmed));
    }

    private void publishConfirmFailed(String orderId, String holdId, String reason) {
        SlotConfirmFailedPayload failed = new SlotConfirmFailedPayload(orderId, holdId, reason);
        afterCommit(() -> eventPublisher.publish(properties.topics().slotConfirmFailed(), orderId, orderId, failed));
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
