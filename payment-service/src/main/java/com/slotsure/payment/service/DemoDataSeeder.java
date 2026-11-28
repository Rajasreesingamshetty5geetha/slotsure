package com.slotsure.payment.service;

import com.slotsure.payment.config.PaymentProperties;
import com.slotsure.payment.dto.LedgerLine;
import com.slotsure.payment.entity.LedgerEntry;
import com.slotsure.payment.entity.PaymentAttempt;
import com.slotsure.payment.entity.PaymentStatus;
import com.slotsure.payment.repository.PaymentAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Seeds the payments behind order-service's demo orders for yesterday and today (CONTRACT V2.10):
 * checkout or online captures into escrow, releases to the merchant for delivered orders, and cash
 * collected for delivered CASH orders. Every movement goes through LedgerService, so the ledger stays
 * balanced. A date is seeded once: when its demo orders already have payments it is left alone.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoDataSeeder {

    /** Share of online payments that gateway A declines and B then takes. */
    private static final double GATEWAY_A_DECLINE_SHARE = 0.2;

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final LedgerService ledgerService;
    private final TransactionTemplate transactionTemplate;
    private final PaymentProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void seedOnStartup() {
        if (!properties.demoData().enabled()) {
            return;
        }
        LocalDate today = LocalDate.now(properties.zone());
        for (LocalDate date : List.of(today.minusDays(1), today)) {
            try {
                seed(date, Instant.now());
            } catch (RuntimeException e) {
                log.error("Could not seed demo payments for {}", date, e);
            }
        }
    }

    /** Returns how many demo orders got payment rows; 0 when the date was seeded before. */
    public int seed(LocalDate date, Instant now) {
        PaymentProperties.DemoData demo = properties.demoData();
        List<DemoPlan.Booking> bookings = DemoPlan.forDate(date, demo.minBookingsPerDay(), demo.maxBookingsPerDay());
        if (paymentAttemptRepository.existsByDemoTrueAndOrderIdIn(bookings.stream().map(DemoPlan.Booking::orderId).toList())) {
            log.info("Demo payments for {} already exist, not seeding", date);
            return 0;
        }
        int[] seeded = {0};
        transactionTemplate.executeWithoutResult(status -> {
            for (DemoPlan.Booking booking : bookings) {
                if (seed(date, booking, now)) {
                    seeded[0]++;
                }
            }
        });
        log.info("Seeded demo payments for {} of {} demo orders on {}", seeded[0], bookings.size(), date);
        return seeded[0];
    }

    private boolean seed(LocalDate date, DemoPlan.Booking booking, Instant now) {
        String orderId = booking.orderId();
        String customerId = DemoPlan.customerId(date, booking.index());
        BigDecimal amount = booking.amount();
        Random random = booking.random();
        Instant slotStart = date.atTime(booking.startHour(), 0).atZone(properties.zone()).toInstant();
        Instant bookedAt = slotStart.minus(Duration.ofMinutes(booking.bookedMinutesBefore()));
        boolean delivered = !booking.failed() && !now.isBefore(slotStart.plus(Duration.ofHours(1)));
        Instant deliveredAt = slotStart.plus(Duration.ofMinutes(booking.deliveredMinute()));

        switch (booking.choice()) {
            case "PREPAID" -> {
                // Paid at checkout, a day or so before the slot was booked.
                Instant paidAt = bookedAt.minus(Duration.ofHours(2L + random.nextInt(22)));
                capture(orderId, customerId, PaymentAttempt.CHECKOUT, PaymentStatus.CAPTURED, amount, paidAt);
            }
            case "ONLINE" -> {
                Instant paidAt = bookedAt.plusSeconds(2);
                boolean aDeclines = booking.failed() || random.nextDouble() < GATEWAY_A_DECLINE_SHARE;
                if (aDeclines) {
                    attempt(orderId, customerId, "A", PaymentStatus.FAILED, amount, paidAt, "gateway A declined the charge");
                }
                if (booking.failed()) {
                    attempt(orderId, customerId, "B", PaymentStatus.FAILED, amount, paidAt.plusSeconds(1),
                            "gateway B declined the charge");
                    return true;
                }
                capture(orderId, customerId, aDeclines ? "B" : "A", PaymentStatus.SUCCESS, amount, paidAt.plusSeconds(1));
            }
            case "CASH" -> {
                if (!delivered) {
                    return false;
                }
                attempt(orderId, customerId, PaymentAttempt.COD, PaymentStatus.COLLECTED, amount, deliveredAt, null);
                ledgerService.post(UUID.randomUUID().toString(), orderId, List.of(
                        LedgerLine.debit(LedgerEntry.CASH_COLLECTED, amount),
                        LedgerLine.credit(LedgerEntry.MERCHANT, amount)), deliveredAt, true);
                return true;
            }
            default -> throw new IllegalStateException("Unknown demo payment choice " + booking.choice());
        }
        if (delivered) {
            ledgerService.post(UUID.randomUUID().toString(), orderId, List.of(
                    LedgerLine.debit(LedgerEntry.ESCROW, amount),
                    LedgerLine.credit(LedgerEntry.MERCHANT, amount)), deliveredAt, true);
        }
        return true;
    }

    /** A successful capture: the payment row plus customer-to-escrow in the ledger. */
    private void capture(String orderId, String customerId, String gateway, PaymentStatus status,
                         BigDecimal amount, Instant at) {
        attempt(orderId, customerId, gateway, status, amount, at, null);
        ledgerService.post(UUID.randomUUID().toString(), orderId, List.of(
                LedgerLine.debit(LedgerEntry.customerAccount(customerId), amount),
                LedgerLine.credit(LedgerEntry.ESCROW, amount)), at, true);
    }

    private void attempt(String orderId, String customerId, String gateway, PaymentStatus status,
                         BigDecimal amount, Instant at, String failureReason) {
        paymentAttemptRepository.save(PaymentAttempt.builder()
                .orderId(orderId)
                .customerId(customerId)
                .gateway(gateway)
                .amount(amount)
                .status(status)
                .failureReason(failureReason)
                .createdAt(at)
                .demo(true)
                .build());
    }
}
