package com.slotsure.slot.service;

import com.slotsure.slot.config.SlotsureProperties;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Runs without a transaction, so after-commit actions execute immediately. */
class ReservationServiceTest {

    private static final String ORDER_ID = "order-1";
    private static final String EVENT_ID = "event-1";

    private ReservationRepository reservationRepository;
    private SlotRepository slotRepository;
    private ProcessedEventRepository processedEventRepository;
    private SlotCounterService slotCounterService;
    private EventPublisher eventPublisher;
    private ReservationService reservationService;
    private Slot slot;

    @BeforeEach
    void setUp() {
        reservationRepository = mock(ReservationRepository.class);
        slotRepository = mock(SlotRepository.class);
        processedEventRepository = mock(ProcessedEventRepository.class);
        slotCounterService = mock(SlotCounterService.class);
        eventPublisher = mock(EventPublisher.class);
        SlotsureProperties properties = new SlotsureProperties(
                new SlotsureProperties.SlotSettings(10, 8, 22),
                new SlotsureProperties.HoldSettings(300, 24, 30000),
                new SlotsureProperties.Topics("slot.confirm", "slot.release", "slot.confirmed",
                        "slot.confirm.failed", "slot.released"));
        reservationService = new ReservationService(reservationRepository, slotRepository, processedEventRepository,
                slotCounterService, eventPublisher, properties);
        slot = new Slot("8960145b4a3ffff", LocalDate.of(2026, 11, 27), 10, 10);
        when(slotRepository.findById(slot.getId())).thenReturn(Optional.of(slot));
    }

    private Reservation hold(Instant expiresAt) {
        Reservation reservation = new Reservation(slot.getId(), "cust-1", "key", Instant.now(), expiresAt);
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));
        return reservation;
    }

    @Test
    void confirmsLiveHoldAndIncrementsBooked() {
        Reservation reservation = hold(Instant.now().plusSeconds(60));

        reservationService.confirm(EVENT_ID, new SlotConfirmPayload(ORDER_ID, reservation.getId()));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(slot.getBooked()).isEqualTo(1);
        verify(processedEventRepository).save(any());
        verify(eventPublisher).publish("slot.confirmed", ORDER_ID, ORDER_ID,
                new SlotConfirmedPayload(ORDER_ID, reservation.getId(), slot.getId(), slot.getH3Cell()));
    }

    @Test
    void confirmOfExpiredHoldPublishesHoldExpired() {
        Reservation reservation = hold(Instant.now().minusSeconds(1));

        reservationService.confirm(EVENT_ID, new SlotConfirmPayload(ORDER_ID, reservation.getId()));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.HELD);
        assertThat(slot.getBooked()).isZero();
        verify(eventPublisher).publish("slot.confirm.failed", ORDER_ID, ORDER_ID,
                new SlotConfirmFailedPayload(ORDER_ID, reservation.getId(), SlotConfirmFailedPayload.HOLD_EXPIRED));
    }

    @Test
    void confirmOfReleasedHoldPublishesHoldExpired() {
        Reservation reservation = hold(Instant.now().plusSeconds(60));
        reservation.setStatus(ReservationStatus.RELEASED);

        reservationService.confirm(EVENT_ID, new SlotConfirmPayload(ORDER_ID, reservation.getId()));

        verify(eventPublisher).publish("slot.confirm.failed", ORDER_ID, ORDER_ID,
                new SlotConfirmFailedPayload(ORDER_ID, reservation.getId(), SlotConfirmFailedPayload.HOLD_EXPIRED));
    }

    @Test
    void confirmOfUnknownHoldPublishesHoldNotFound() {
        when(reservationRepository.findById("nope")).thenReturn(Optional.empty());

        reservationService.confirm(EVENT_ID, new SlotConfirmPayload(ORDER_ID, "nope"));

        verify(eventPublisher).publish("slot.confirm.failed", ORDER_ID, ORDER_ID,
                new SlotConfirmFailedPayload(ORDER_ID, "nope", SlotConfirmFailedPayload.HOLD_NOT_FOUND));
    }

    @Test
    void skipsAlreadyProcessedEvent() {
        Reservation reservation = hold(Instant.now().plusSeconds(60));
        when(processedEventRepository.existsById(EVENT_ID)).thenReturn(true);

        reservationService.confirm(EVENT_ID, new SlotConfirmPayload(ORDER_ID, reservation.getId()));
        reservationService.release(EVENT_ID, new SlotReleasePayload(ORDER_ID, reservation.getId(), "PAYMENT_FAILED"));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.HELD);
        verify(processedEventRepository, never()).save(any());
        verify(eventPublisher, never()).publish(anyString(), anyString(), anyString(), any());
    }

    @Test
    void releaseOfConfirmedHoldDecrementsBookedAndReturnsPlace() {
        Reservation reservation = hold(Instant.now().plusSeconds(60));
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setOrderId(ORDER_ID);
        slot.setBooked(3);

        reservationService.release(EVENT_ID, new SlotReleasePayload(ORDER_ID, reservation.getId(), "REFUND"));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(slot.getBooked()).isEqualTo(2);
        verify(slotCounterService).giveBack(slot.getId());
        verify(eventPublisher).publish("slot.released", reservation.getId(), ORDER_ID,
                new SlotReleasedPayload(reservation.getId(), slot.getId(), SlotReleasedPayload.COMPENSATION));
    }

    @Test
    void releaseOfHeldHoldLeavesBookedAlone() {
        Reservation reservation = hold(Instant.now().plusSeconds(60));
        slot.setBooked(3);

        reservationService.release(EVENT_ID, new SlotReleasePayload(ORDER_ID, reservation.getId(), "PAYMENT_FAILED"));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(slot.getBooked()).isEqualTo(3);
        verify(slotCounterService).giveBack(slot.getId());
    }

    @Test
    void releaseOfReleasedHoldDoesNothing() {
        Reservation reservation = hold(Instant.now().plusSeconds(60));
        reservation.setStatus(ReservationStatus.RELEASED);

        reservationService.release(EVENT_ID, new SlotReleasePayload(ORDER_ID, reservation.getId(), "PAYMENT_FAILED"));

        verify(slotCounterService, never()).giveBack(anyString());
        verify(eventPublisher, never()).publish(anyString(), anyString(), anyString(), any());
    }

    @Test
    void expireReleasesOnlyExpiredHeldHolds() {
        Reservation expired = hold(Instant.now().minusSeconds(1));
        Reservation live = hold(Instant.now().plusSeconds(60));

        assertThat(reservationService.expire(expired.getId())).isTrue();
        assertThat(reservationService.expire(live.getId())).isFalse();

        assertThat(expired.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(live.getStatus()).isEqualTo(ReservationStatus.HELD);
        verify(slotCounterService).giveBack(slot.getId());
        verify(eventPublisher).publish("slot.released", expired.getId(), expired.getId(),
                new SlotReleasedPayload(expired.getId(), slot.getId(), SlotReleasedPayload.EXPIRED));
    }
}
