package com.slotsure.order.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class DemoPlanTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 28);

    @Test
    void theSameDateAlwaysGivesTheSameBookings() {
        List<DemoPlan.Booking> first = DemoPlan.forDate(DATE, 40, 80);
        List<DemoPlan.Booking> second = DemoPlan.forDate(DATE, 40, 80);

        assertThat(second).hasSameSizeAs(first);
        for (int i = 0; i < first.size(); i++) {
            assertThat(second.get(i).orderId()).isEqualTo(first.get(i).orderId());
            assertThat(second.get(i).areaName()).isEqualTo(first.get(i).areaName());
            assertThat(second.get(i).startHour()).isEqualTo(first.get(i).startHour());
            assertThat(second.get(i).agent()).isEqualTo(first.get(i).agent());
            assertThat(second.get(i).failed()).isEqualTo(first.get(i).failed());
        }
    }

    /** dispatch-service's copy of DemoPlan has the same test; both must print the same fingerprint. */
    @Test
    void fingerprintMatchesDispatchService() {
        assertThat(DemoPlanFingerprint.of(DemoPlan.forDate(DATE, 40, 80)))
                .isEqualTo(DemoPlanFingerprint.EXPECTED_2026_11_28);
    }

    @Test
    void bookingsLookLikeARealDay() {
        List<DemoPlan.Booking> bookings = DemoPlan.forDate(DATE, 40, 80);

        assertThat(bookings.size()).isEqualTo(40 + new Random(DemoPlan.seed("slotsure-demo-day:" + DATE)).nextInt(41));
        assertThat(bookings).allSatisfy(booking -> {
            assertThat(booking.startHour()).isBetween(8, 21);
            assertThat(DemoPlan.AREAS).contains(booking.areaName());
            assertThat(booking.agent().homeArea()).isEqualTo(booking.areaName());
            assertThat(booking.deliveredMinute()).isBetween(10, 55);
        });
        long evening = bookings.stream().filter(b -> b.startHour() >= 18 && b.startHour() < 21).count();
        assertThat(evening * 100 / bookings.size()).as("share 18:00-21:00").isGreaterThanOrEqualTo(30);
        assertThat(bookings.stream().filter(DemoPlan.Booking::failed).count()).isLessThan(bookings.size() / 5);
    }

    /** Neighbouring seeds once put a whole day in one area with one agent; every day must be varied. */
    @Test
    void everyDayIsSpreadOverAreasHoursAgentsAndItems() {
        for (int day = 0; day < 60; day++) {
            List<DemoPlan.Booking> bookings = DemoPlan.forDate(DATE.plusDays(day), 40, 80);

            assertThat(bookings.stream().map(DemoPlan.Booking::areaName).distinct().count()).as("areas").isGreaterThanOrEqualTo(8);
            assertThat(bookings.stream().map(DemoPlan.Booking::startHour).distinct().count()).as("hours").isGreaterThanOrEqualTo(10);
            assertThat(bookings.stream().map(b -> b.agent().agentId()).distinct().count()).as("agents").isGreaterThanOrEqualTo(9);
            assertThat(bookings.stream().map(DemoPlan.Booking::itemIndex).distinct().count()).as("items").isGreaterThanOrEqualTo(9);
            assertThat(bookings.stream().map(DemoPlan.Booking::choice).distinct().count()).as("choices").isEqualTo(3);
        }
    }

    @Test
    void itemPricesFollowTheCatalog() {
        assertThat(DemoPlan.ITEM_PRICES).containsExactlyElementsOf(
                PurchaseService.CATALOG.stream().map(PurchaseService.CatalogItem::amount).toList());
    }

    @Test
    void aboutAThirdOfBookingsAreCashOnDelivery() {
        List<DemoPlan.Booking> bookings = DemoPlan.forDate(DATE, 40, 80);

        long cod = bookings.stream().filter(b -> !b.choice().equals("PREPAID")).count();
        assertThat(cod * 100 / bookings.size()).isBetween(15L, 55L);
        assertThat(bookings).allSatisfy(b -> {
            assertThat(b.choice()).isIn("PREPAID", "ONLINE", "CASH");
            assertThat(b.amount()).isEqualTo(DemoPlan.ITEM_PRICES.get(b.itemIndex()));
            assertThat(b.bookedMinutesBefore()).isBetween(60, 60 + 30 * 60 - 1);
        });
    }

    @Test
    void twelveAgentsCoverEveryArea() {
        assertThat(DemoPlan.AGENTS).hasSize(12);
        assertThat(DemoPlan.AGENTS.stream().map(DemoPlan.Agent::homeArea).distinct()).hasSize(10);
        assertThat(DemoPlan.AGENTS.stream().map(DemoPlan.Agent::agentId).distinct()).hasSize(12);
    }
}
