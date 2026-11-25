package com.slotsure.slot.service;

import com.slotsure.slot.config.SlotsureProperties;
import com.slotsure.slot.dto.PriceTag;
import com.slotsure.slot.dto.SlotResponse;
import com.slotsure.slot.entity.Slot;
import com.slotsure.slot.exception.ApiException;
import com.slotsure.slot.repository.SlotRepository;
import com.uber.h3core.H3Core;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlotServiceTest {

    private static final double LAT = 12.9716;
    private static final double LNG = 77.5946;
    private static final LocalDate DATE = LocalDate.of(2026, 11, 27);

    private static H3Core h3Core;

    private SlotRepository slotRepository;
    private SlotCounterService slotCounterService;
    private SlotService slotService;

    @BeforeAll
    static void loadH3() throws IOException {
        h3Core = H3Core.newInstance();
    }

    @BeforeEach
    void setUp() {
        slotRepository = mock(SlotRepository.class);
        slotCounterService = mock(SlotCounterService.class);
        SlotsureProperties properties = new SlotsureProperties(
                new SlotsureProperties.SlotSettings(10, 8, 22),
                new SlotsureProperties.HoldSettings(300, 24, 30000),
                new SlotsureProperties.Topics("slot.confirm", "slot.release", "slot.confirmed",
                        "slot.confirm.failed", "slot.released"));
        TransactionTemplate transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));
        slotService = new SlotService(slotRepository, slotCounterService, h3Core, transactionTemplate, properties);
    }

    @Test
    void convertsLatLngToResolutionNineCell() {
        String cell = slotService.toCell(LAT, LNG);

        assertThat(h3Core.isValidCell(cell)).isTrue();
        assertThat(h3Core.getResolution(cell)).isEqualTo(9);
    }

    @Test
    void rejectsOutOfRangeCoordinates() {
        assertThatThrownBy(() -> slotService.toCell(91, 0))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("BAD_REQUEST");
    }

    @Test
    @SuppressWarnings("unchecked")
    void createsFourteenOneHourSlotsWhenNoneExist() {
        String cell = slotService.toCell(LAT, LNG);
        ArgumentCaptor<List<Slot>> saved = ArgumentCaptor.forClass(List.class);
        when(slotRepository.findByH3CellAndSlotDateOrderByStartHour(cell, DATE)).thenReturn(List.of());
        when(slotRepository.saveAllAndFlush(saved.capture())).thenAnswer(inv -> inv.getArgument(0));
        when(slotCounterService.remaining(anyList())).thenReturn(Map.of());

        slotService.findSlots(LAT, LNG, DATE);

        List<Slot> slots = saved.getValue();
        assertThat(slots).hasSize(14);
        assertThat(slots).extracting(Slot::getStartHour).containsExactly(8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21);
        assertThat(slots).allSatisfy(slot -> {
            assertThat(slot.getCapacity()).isEqualTo(10);
            assertThat(slot.getH3Cell()).isEqualTo(cell);
            assertThat(slot.getSlotDate()).isEqualTo(DATE);
        });
    }

    @Test
    void reReadsSlotsWhenAConcurrentRequestCreatedThemFirst() {
        String cell = slotService.toCell(LAT, LNG);
        List<Slot> existing = List.of(new Slot(cell, DATE, 8, 10), new Slot(cell, DATE, 9, 10));
        when(slotRepository.findByH3CellAndSlotDateOrderByStartHour(cell, DATE))
                .thenReturn(List.of())
                .thenReturn(existing);
        when(slotRepository.saveAllAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        when(slotCounterService.remaining(existing)).thenReturn(Map.of(
                existing.get(0).getId(), 10L,
                existing.get(1).getId(), 1L));

        List<SlotResponse> result = slotService.findSlots(LAT, LNG, DATE);

        assertThat(result).hasSize(2);
        Map<Integer, SlotResponse> byHour = result.stream()
                .collect(Collectors.toMap(SlotResponse::startHour, Function.identity()));
        assertThat(byHour.get(8).priceTag()).isEqualTo(PriceTag.GREEN);
        assertThat(byHour.get(8).endHour()).isEqualTo(9);
        assertThat(byHour.get(9).remaining()).isEqualTo(1);
        assertThat(byHour.get(9).priceTag()).isEqualTo(PriceTag.BUSY);
    }

    @Test
    void doesNotCreateSlotsWhenTheyAlreadyExist() {
        String cell = slotService.toCell(LAT, LNG);
        List<Slot> existing = List.of(new Slot(cell, DATE, 8, 10));
        when(slotRepository.findByH3CellAndSlotDateOrderByStartHour(cell, DATE)).thenReturn(existing);
        when(slotCounterService.remaining(existing)).thenReturn(Map.of(existing.get(0).getId(), 5L));

        List<SlotResponse> result = slotService.findSlots(LAT, LNG, DATE);

        assertThat(result).singleElement().satisfies(slot -> assertThat(slot.priceTag()).isEqualTo(PriceTag.NORMAL));
        verify(slotRepository, never()).saveAllAndFlush(any());
    }
}
