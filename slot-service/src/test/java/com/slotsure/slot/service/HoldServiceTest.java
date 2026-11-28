package com.slotsure.slot.service;

import com.slotsure.slot.TestProperties;
import com.slotsure.slot.config.SlotsureProperties;
import com.slotsure.slot.dto.HoldRequest;
import com.slotsure.slot.dto.HoldResponse;
import com.slotsure.slot.entity.Reservation;
import com.slotsure.slot.entity.ReservationStatus;
import com.slotsure.slot.exception.ApiException;
import com.slotsure.slot.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HoldServiceTest {

    private static final String SLOT_ID = "slot-1";
    private static final String KEY = "key-1";

    private ReservationRepository reservationRepository;
    private SlotCounterService slotCounterService;
    private ValueOperations<String, String> values;
    private HoldService holdService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        reservationRepository = mock(ReservationRepository.class);
        slotCounterService = mock(SlotCounterService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        SlotsureProperties properties = TestProperties.defaults();
        holdService = new HoldService(reservationRepository, slotCounterService, redis, properties);
    }

    @Test
    void createsHeldReservationAndStoresIdempotencyKey() {
        when(slotCounterService.tryTake(SLOT_ID)).thenReturn(true);
        ArgumentCaptor<Reservation> saved = ArgumentCaptor.forClass(Reservation.class);
        when(reservationRepository.saveAndFlush(saved.capture())).thenAnswer(inv -> inv.getArgument(0));
        Instant before = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);

        HoldResponse response = holdService.createHold(KEY, new HoldRequest(SLOT_ID, "cust-1"));

        Reservation reservation = saved.getValue();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.HELD);
        assertThat(reservation.getIdempotencyKey()).isEqualTo(KEY);
        assertThat(reservation.getExpiresAt()).isBetween(before.plusSeconds(300), Instant.now().plusSeconds(300));
        assertThat(response.holdId()).isEqualTo(reservation.getId());
        assertThat(response.slotId()).isEqualTo(SLOT_ID);
        verify(values).set("idem:" + KEY, reservation.getId(), Duration.ofHours(24));
    }

    @Test
    void replaysExistingHoldForKnownIdempotencyKey() {
        Reservation existing = new Reservation(SLOT_ID, "cust-1", KEY, Instant.now(), Instant.now().plusSeconds(300));
        when(values.get("idem:" + KEY)).thenReturn(existing.getId());
        when(reservationRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        HoldResponse response = holdService.createHold(KEY, new HoldRequest(SLOT_ID, "cust-1"));

        assertThat(response.holdId()).isEqualTo(existing.getId());
        verify(slotCounterService, never()).tryTake(anyString());
        verify(reservationRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsWithSlotFullWhenCounterIsEmpty() {
        when(slotCounterService.tryTake(SLOT_ID)).thenReturn(false);

        assertThatThrownBy(() -> holdService.createHold(KEY, new HoldRequest(SLOT_ID, "cust-1")))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("SLOT_FULL");
        verify(reservationRepository, never()).saveAndFlush(any());
    }

    @Test
    void givesPlaceBackWhenSavingFails() {
        when(slotCounterService.tryTake(SLOT_ID)).thenReturn(true);
        when(reservationRepository.saveAndFlush(any())).thenThrow(new QueryTimeoutException("db down"));

        assertThatThrownBy(() -> holdService.createHold(KEY, new HoldRequest(SLOT_ID, "cust-1")))
                .isInstanceOf(QueryTimeoutException.class);
        verify(slotCounterService).giveBack(SLOT_ID);
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void returnsWinnerWhenSameKeyRacesAndGivesPlaceBack() {
        Reservation winner = new Reservation(SLOT_ID, "cust-1", KEY, Instant.now(), Instant.now().plusSeconds(300));
        when(slotCounterService.tryTake(SLOT_ID)).thenReturn(true);
        when(reservationRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(reservationRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(winner));

        HoldResponse response = holdService.createHold(KEY, new HoldRequest(SLOT_ID, "cust-1"));

        assertThat(response.holdId()).isEqualTo(winner.getId());
        verify(slotCounterService).giveBack(SLOT_ID);
    }

    @Test
    void requiresIdempotencyKey() {
        assertThatThrownBy(() -> holdService.createHold(" ", new HoldRequest(SLOT_ID, "cust-1")))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("BAD_REQUEST");
    }

    @Test
    void requiresSlotAndCustomer() {
        assertThatThrownBy(() -> holdService.createHold(KEY, new HoldRequest(null, "cust-1")))
                .isInstanceOf(ApiException.class);
        verify(slotCounterService, never()).tryTake(eq(SLOT_ID));
    }

    @Test
    void getHoldReturnsNotFoundForUnknownId() {
        when(reservationRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> holdService.getHold("missing"))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("HOLD_NOT_FOUND");
    }
}
