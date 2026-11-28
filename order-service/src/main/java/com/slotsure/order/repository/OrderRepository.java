package com.slotsure.order.repository;

import com.slotsure.order.entity.Order;
import com.slotsure.order.entity.OrderStatus;
import com.slotsure.order.entity.PaymentChoice;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, String> {

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    List<Order> findByCustomerIdOrderByCreatedAtDesc(String customerId);

    List<Order> findBySlotDateOrderByCreatedAtDesc(LocalDate slotDate);

    Optional<Order> findFirstByPurchaseIdAndStatusIn(String purchaseId, Collection<OrderStatus> statuses);

    boolean existsBySlotDateAndDemoTrue(LocalDate slotDate);

    /** One row per status and payment choice: the admin KPIs and money totals are sums of these. */
    @Query("""
            select o.status as status, o.paymentChoice as paymentChoice,
                   count(o) as orders, coalesce(sum(o.amount), 0) as amount
            from Order o
            where o.slotDate = :date
            group by o.status, o.paymentChoice
            """)
    List<StatusTotals> totalsByStatus(@Param("date") LocalDate date);

    @Query("""
            select o.itemName as itemName, count(o) as bookings
            from Order o
            where o.slotDate = :date and o.status in :statuses and o.itemName is not null
            group by o.itemName
            order by count(o) desc, o.itemName
            """)
    List<ItemTotals> topItems(@Param("date") LocalDate date, @Param("statuses") Collection<OrderStatus> statuses,
                              Pageable pageable);

    @Query("""
            select o.startHour as startHour, count(o) as bookings
            from Order o
            where o.slotDate = :date and o.status in :statuses and o.startHour is not null
            group by o.startHour
            order by o.startHour
            """)
    List<HourTotals> bookingsByHour(@Param("date") LocalDate date, @Param("statuses") Collection<OrderStatus> statuses);

    @Query("""
            select o.areaName as areaName, count(o) as bookings
            from Order o
            where o.slotDate = :date and o.status in :statuses and o.areaName is not null
            group by o.areaName
            order by count(o) desc, o.areaName
            """)
    List<AreaTotals> bookingsByArea(@Param("date") LocalDate date, @Param("statuses") Collection<OrderStatus> statuses);

    interface StatusTotals {
        OrderStatus getStatus();

        PaymentChoice getPaymentChoice();

        long getOrders();

        BigDecimal getAmount();
    }

    interface ItemTotals {
        String getItemName();

        long getBookings();
    }

    interface HourTotals {
        int getStartHour();

        long getBookings();
    }

    interface AreaTotals {
        String getAreaName();

        long getBookings();
    }
}
