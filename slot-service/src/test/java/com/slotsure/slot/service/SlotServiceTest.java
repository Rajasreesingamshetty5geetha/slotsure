package com.slotsure.slot.service;

import com.slotsure.slot.TestProperties;
import com.slotsure.slot.dto.AdminSlotSummary;
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
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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
    private AreaResolver areaResolver;
    private SlotService slotService;

    @BeforeAll
    static void loadH3() throws IOException {
        h3Core = H3Core.newInstance();
    }

    @BeforeEach
    void setUp() {
        slotRepository = mock(SlotRepository.class);
        slotCounterService = mock(SlotCounterService.class);
        areaResolver = new AreaResolver(h3Core, TestProperties.defaults());
        TransactionTemplate transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));
        slotService = new SlotService(slotRepository, slotCounterService, areaResolver, h3Core,
                transactionTemplate, TestProperties.defaults());
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
    void createsFourteenNamedOneHourSlotsWhenNoneExist() {
        String cell = slotService.toCell(12.9352, 77.6245);
        ArgumentCaptor<List<Slot>> saved = ArgumentCaptor.forClass(List.class);
        when(slotRepository.findByH3CellAndSlotDateOrderByStartHour(cell, DATE)).thenReturn(List.of());
        when(slotRepository.saveAllAndFlush(saved.capture())).thenAnswer(inv -> inv.getArgument(0));
        when(slotCounterService.remaining(anyList())).thenReturn(Map.of());

        slotService.findSlots(12.9352, 77.6245, DATE);

        List<Slot> slots = saved.getValue();
        assertThat(slots).hasSize(14);
        assertThat(slots).extracting(Slot::getStartHour).containsExactly(8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21);
        assertThat(slots).allSatisfy(slot -> {
            assertThat(slot.getCapacity()).isEqualTo(10);
            assertThat(slot.getH3Cell()).isEqualTo(cell);
            assertThat(slot.getAreaName()).isEqualTo("Koramangala");
            assertThat(slot.getSlotDate()).isEqualTo(DATE);
            assertThat(slot.isDemo()).isFalse();
        });
    }

    @Test
    void reReadsSlotsWhenAConcurrentRequestCreatedThemFirst() {
        String cell = slotService.toCell(LAT, LNG);
        List<Slot> existing = List.of(new Slot(cell, "Jayanagar", DATE, 8, 10), new Slot(cell, "Jayanagar", DATE, 9, 10));
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
        assertThat(byHour.get(8).areaName()).isEqualTo("Jayanagar");
        assertThat(byHour.get(9).remaining()).isEqualTo(1);
        assertThat(byHour.get(9).priceTag()).isEqualTo(PriceTag.BUSY);
    }

    @Test
    void doesNotCreateSlotsWhenTheyAlreadyExist() {
        String cell = slotService.toCell(LAT, LNG);
        List<Slot> existing = List.of(new Slot(cell, "Jayanagar", DATE, 8, 10));
        when(slotRepository.findByH3CellAndSlotDateOrderByStartHour(cell, DATE)).thenReturn(existing);
        when(slotCounterService.remaining(existing)).thenReturn(Map.of(existing.get(0).getId(), 5L));

        List<SlotResponse> result = slotService.findSlots(LAT, LNG, DATE);

        assertThat(result).singleElement().satisfies(slot -> assertThat(slot.priceTag()).isEqualTo(PriceTag.NORMAL));
        verify(slotRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void namesALegacySlotOnTheFlyWhenItHasNoAreaYet() {
        String cell = slotService.toCell(12.9784, 77.6408);
        List<Slot> existing = List.of(new Slot(cell, null, DATE, 8, 10));
        when(slotRepository.findByH3CellAndSlotDateOrderByStartHour(cell, DATE)).thenReturn(existing);
        when(slotCounterService.remaining(existing)).thenReturn(Map.of(existing.get(0).getId(), 10L));

        List<SlotResponse> result = slotService.findSlots(12.9784, 77.6408, DATE);

        assertThat(result).singleElement().satisfies(slot -> assertThat(slot.areaName()).isEqualTo("Indiranagar"));
    }

    @Test
    void adminSummaryTotalsAreasAndListsBusiestSlots() {
        when(slotRepository.totalsByArea(DATE)).thenReturn(List.of(
                areaTotals("Koramangala", 140, 21),
                areaTotals("Hebbal", 140, 6),
                areaTotals(null, 14, 0)));
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        when(slotRepository.busiestSlots(eq(DATE), page.capture())).thenReturn(List.of(
                hourTotals("Koramangala", 19, 10, 6),
                hourTotals("Koramangala", 18, 10, 5),
                hourTotals("Hebbal", 20, 10, 3)));

        AdminSlotSummary summary = slotService.adminSummary(DATE);

        assertThat(summary.date()).isEqualTo(DATE);
        assertThat(summary.totalCapacity()).isEqualTo(294);
        assertThat(summary.totalBooked()).isEqualTo(27);
        assertThat(summary.utilisationPct()).isEqualTo(9.2); // 27 / 294 = 9.18%
        assertThat(summary.byArea()).containsExactly(
                new AdminSlotSummary.AreaLoad("Koramangala", 140, 21),
                new AdminSlotSummary.AreaLoad("Hebbal", 140, 6),
                new AdminSlotSummary.AreaLoad(AreaResolver.UNKNOWN, 14, 0));
        assertThat(summary.busiest()).containsExactly(
                new AdminSlotSummary.BusySlot("Koramangala", 19, 20, 6, 10),
                new AdminSlotSummary.BusySlot("Koramangala", 18, 19, 5, 10),
                new AdminSlotSummary.BusySlot("Hebbal", 20, 21, 3, 10));
        assertThat(page.getValue().getPageSize()).isEqualTo(5);
        assertThat(page.getValue().getPageNumber()).isZero();
    }

    @Test
    void adminSummaryForADayWithoutSlotsIsAllZero() {
        when(slotRepository.totalsByArea(DATE)).thenReturn(List.of());
        when(slotRepository.busiestSlots(eq(DATE), any())).thenReturn(List.of());

        AdminSlotSummary summary = slotService.adminSummary(DATE);

        assertThat(summary.totalCapacity()).isZero();
        assertThat(summary.totalBooked()).isZero();
        assertThat(summary.utilisationPct()).isZero();
        assertThat(summary.byArea()).isEmpty();
        assertThat(summary.busiest()).isEmpty();
    }

    @Test
    void utilisationRoundsToOneDecimal() {
        assertThat(SlotService.utilisationPct(1, 3)).isEqualTo(33.3);
        assertThat(SlotService.utilisationPct(2, 3)).isEqualTo(66.7);
        assertThat(SlotService.utilisationPct(10, 10)).isEqualTo(100.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void backfillNamesUnnamedCellsWithOneUpdatePerArea() {
        String koramangala1 = slotService.toCell(12.9352, 77.6245);
        String koramangala2 = slotService.toCell(12.9330, 77.6270);
        String hebbal = slotService.toCell(13.0358, 77.5970);
        when(slotRepository.findCellsWithoutAreaName()).thenReturn(List.of(koramangala1, hebbal, koramangala2));
        ArgumentCaptor<Collection<String>> cells = ArgumentCaptor.forClass(Collection.class);
        when(slotRepository.setAreaName(cells.capture(), eq("Koramangala"))).thenReturn(28);
        when(slotRepository.setAreaName(any(), eq("Hebbal"))).thenReturn(14);

        slotService.backfillAreaNames();

        assertThat(cells.getValue()).containsExactlyInAnyOrder(koramangala1, koramangala2);
        verify(slotRepository).setAreaName(List.of(hebbal), "Hebbal");
    }

    private static SlotRepository.AreaTotals areaTotals(String areaName, long capacity, long booked) {
        return new SlotRepository.AreaTotals() {
            public String getAreaName() {
                return areaName;
            }

            public long getCapacity() {
                return capacity;
            }

            public long getBooked() {
                return booked;
            }
        };
    }

    private static SlotRepository.HourTotals hourTotals(String areaName, int startHour, long capacity, long booked) {
        return new SlotRepository.HourTotals() {
            public String getAreaName() {
                return areaName;
            }

            public int getStartHour() {
                return startHour;
            }

            public long getCapacity() {
                return capacity;
            }

            public long getBooked() {
                return booked;
            }
        };
    }
}
