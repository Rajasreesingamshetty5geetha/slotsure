package com.slotsure.order.service;

import com.slotsure.order.config.OrderProperties;
import com.slotsure.order.dto.AdminSummaryResponse;
import com.slotsure.order.entity.OrderStatus;
import com.slotsure.order.entity.PaymentChoice;
import com.slotsure.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Dashboard numbers for one slot date, computed with GROUP BY queries rather than by loading orders. */
@Service
@RequiredArgsConstructor
public class AdminService {

    static final int TOP_ITEMS = 5;
    private static final Set<OrderStatus> FAILED = EnumSet.of(OrderStatus.FAILED, OrderStatus.CANCELLED);
    private static final Set<OrderStatus> CASH_DUE = EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.OUT_FOR_DELIVERY);

    private final OrderRepository orderRepository;
    private final OrderProperties properties;

    public LocalDate today() {
        return LocalDate.now(properties.zone());
    }

    @Transactional(readOnly = true)
    public AdminSummaryResponse summary(LocalDate date) {
        long total = 0;
        long confirmed = 0;
        long failed = 0;
        long delivered = 0;
        BigDecimal prepaid = BigDecimal.ZERO;
        BigDecimal online = BigDecimal.ZERO;
        BigDecimal codExpected = BigDecimal.ZERO;
        BigDecimal codCollected = BigDecimal.ZERO;

        for (OrderRepository.StatusTotals row : orderRepository.totalsByStatus(date)) {
            OrderStatus status = row.getStatus();
            PaymentChoice choice = PaymentChoice.orDefault(row.getPaymentChoice());
            BigDecimal amount = row.getAmount() == null ? BigDecimal.ZERO : row.getAmount();
            total += row.getOrders();
            if (OrderStatus.BOOKED.contains(status)) {
                confirmed += row.getOrders();
                if (choice == PaymentChoice.PREPAID) {
                    prepaid = prepaid.add(amount);
                } else if (choice == PaymentChoice.ONLINE) {
                    online = online.add(amount);
                }
            }
            if (FAILED.contains(status)) {
                failed += row.getOrders();
            }
            if (status == OrderStatus.DELIVERED) {
                delivered += row.getOrders();
            }
            if (choice == PaymentChoice.CASH && CASH_DUE.contains(status)) {
                codExpected = codExpected.add(amount);
            }
            if (choice == PaymentChoice.CASH && status == OrderStatus.DELIVERED) {
                codCollected = codCollected.add(amount);
            }
        }

        List<AdminSummaryResponse.ItemCount> topItems = orderRepository
                .topItems(date, OrderStatus.BOOKED, PageRequest.of(0, TOP_ITEMS)).stream()
                .map(row -> new AdminSummaryResponse.ItemCount(row.getItemName(), row.getBookings()))
                .toList();
        List<AdminSummaryResponse.HourCount> byHour = orderRepository.bookingsByHour(date, OrderStatus.BOOKED).stream()
                .map(row -> new AdminSummaryResponse.HourCount(row.getStartHour(), row.getBookings()))
                .toList();
        List<AdminSummaryResponse.AreaCount> byArea = orderRepository.bookingsByArea(date, OrderStatus.BOOKED).stream()
                .map(row -> new AdminSummaryResponse.AreaCount(row.getAreaName(), row.getBookings()))
                .toList();

        return new AdminSummaryResponse(date, total, confirmed, failed, delivered,
                money(prepaid), money(online), money(codExpected), money(codCollected), topItems, byHour, byArea);
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
