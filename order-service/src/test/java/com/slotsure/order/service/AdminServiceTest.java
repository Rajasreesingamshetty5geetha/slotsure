package com.slotsure.order.service;

import com.slotsure.order.InMemoryStore;
import com.slotsure.order.dto.AdminSummaryResponse;
import com.slotsure.order.entity.OrderStatus;
import com.slotsure.order.entity.PaymentChoice;
import com.slotsure.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 28);

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final AdminService adminService = new AdminService(orderRepository, InMemoryStore.PROPERTIES);

    @Test
    void summaryAddsUpStatusesAndMoneyByPaymentChoice() {
        when(orderRepository.totalsByStatus(DATE)).thenReturn(List.of(
                totals(OrderStatus.DELIVERED, PaymentChoice.PREPAID, 10, "25000.00"),
                totals(OrderStatus.OUT_FOR_DELIVERY, PaymentChoice.PREPAID, 5, "12000.00"),
                totals(OrderStatus.CONFIRMED, PaymentChoice.ONLINE, 2, "6000.00"),
                totals(OrderStatus.OUT_FOR_DELIVERY, PaymentChoice.CASH, 4, "8000.00"),
                totals(OrderStatus.DELIVERED, PaymentChoice.CASH, 3, "4500.00"),
                totals(OrderStatus.FAILED, PaymentChoice.ONLINE, 2, "3000.00"),
                totals(OrderStatus.CANCELLED, PaymentChoice.ONLINE, 1, "999.00"),
                totals(OrderStatus.PENDING, PaymentChoice.CASH, 1, "500.00")));
        when(orderRepository.topItems(eq(DATE), anyCollection(), any(Pageable.class))).thenReturn(List.of(
                item("Wireless Headphones", 7), item("Air Fryer 4L", 4)));
        when(orderRepository.bookingsByHour(eq(DATE), anyCollection())).thenReturn(List.of(hour(18, 9), hour(19, 11)));
        when(orderRepository.bookingsByArea(eq(DATE), anyCollection())).thenReturn(List.of(area("Koramangala", 8)));

        AdminSummaryResponse summary = adminService.summary(DATE);

        assertThat(summary.totalBookings()).isEqualTo(28);
        assertThat(summary.confirmed()).isEqualTo(24);
        assertThat(summary.failed()).isEqualTo(3);
        assertThat(summary.delivered()).isEqualTo(13);
        assertThat(summary.prepaidAmount()).isEqualTo(new BigDecimal("37000.00"));
        assertThat(summary.onlineAmount()).isEqualTo(new BigDecimal("6000.00"));
        assertThat(summary.codExpected()).isEqualTo(new BigDecimal("8000.00"));
        assertThat(summary.codCollected()).isEqualTo(new BigDecimal("4500.00"));
        assertThat(summary.topItems()).containsExactly(
                new AdminSummaryResponse.ItemCount("Wireless Headphones", 7),
                new AdminSummaryResponse.ItemCount("Air Fryer 4L", 4));
        assertThat(summary.byHour()).containsExactly(
                new AdminSummaryResponse.HourCount(18, 9), new AdminSummaryResponse.HourCount(19, 11));
        assertThat(summary.byArea()).containsExactly(new AdminSummaryResponse.AreaCount("Koramangala", 8));
    }

    @Test
    void aQuietDayIsAllZero() {
        when(orderRepository.totalsByStatus(DATE)).thenReturn(List.of());

        AdminSummaryResponse summary = adminService.summary(DATE);

        assertThat(summary.totalBookings()).isZero();
        assertThat(summary.prepaidAmount()).isEqualTo(new BigDecimal("0.00"));
        assertThat(summary.topItems()).isEmpty();
    }

    private static OrderRepository.StatusTotals totals(OrderStatus status, PaymentChoice choice, long orders, String amount) {
        return new OrderRepository.StatusTotals() {
            public OrderStatus getStatus() {
                return status;
            }

            public PaymentChoice getPaymentChoice() {
                return choice;
            }

            public long getOrders() {
                return orders;
            }

            public BigDecimal getAmount() {
                return new BigDecimal(amount);
            }
        };
    }

    private static OrderRepository.ItemTotals item(String name, long bookings) {
        return new OrderRepository.ItemTotals() {
            public String getItemName() {
                return name;
            }

            public long getBookings() {
                return bookings;
            }
        };
    }

    private static OrderRepository.HourTotals hour(int startHour, long bookings) {
        return new OrderRepository.HourTotals() {
            public int getStartHour() {
                return startHour;
            }

            public long getBookings() {
                return bookings;
            }
        };
    }

    private static OrderRepository.AreaTotals area(String name, long bookings) {
        return new OrderRepository.AreaTotals() {
            public String getAreaName() {
                return name;
            }

            public long getBookings() {
                return bookings;
            }
        };
    }
}
