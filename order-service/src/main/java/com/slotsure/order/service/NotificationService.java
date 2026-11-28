package com.slotsure.order.service;

import com.slotsure.order.config.OrderProperties;
import com.slotsure.order.dto.NotificationResponse;
import com.slotsure.order.entity.Notification;
import com.slotsure.order.entity.NotificationType;
import com.slotsure.order.entity.Order;
import com.slotsure.order.entity.PaymentChoice;
import com.slotsure.order.exception.ApiException;
import com.slotsure.order.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Writes the customer-facing notification for each order milestone. Callers run inside the
 * transaction that changes the order status, so the notification commits (or rolls back) with it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int MAX_RESULTS = 100;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final NotificationRepository notificationRepository;
    private final OrderProperties properties;

    public void slotConfirmed(Order order) {
        String payment = switch (order.effectivePaymentChoice()) {
            case PREPAID -> "Already paid · held in escrow until delivery.";
            case ONLINE -> "Payment of " + rupees(order.getAmount()) + " is held in escrow until delivery.";
            case CASH -> "Pay " + rupees(order.getAmount()) + " to the agent on delivery (cash or UPI).";
        };
        save(order, NotificationType.SLOT_CONFIRMED,
                "Slot confirmed — " + item(order) + " arrives " + when(order),
                payment + " We'll tell you when an agent is assigned.");
    }

    public void agentAssigned(Order order) {
        String body = "Arriving " + when(order) + ". " + order.getAgentName() + " can be reached on "
                + order.getAgentPhone() + ".";
        if (order.effectivePaymentChoice() == PaymentChoice.CASH) {
            body += " Keep " + rupees(order.getAmount()) + " ready (cash or UPI).";
        }
        save(order, NotificationType.AGENT_ASSIGNED,
                "Out for delivery — " + order.getAgentName() + " is bringing your " + item(order), body);
    }

    public void delivered(Order order) {
        String at = order.getDeliveredAt() == null ? "" : " at " + CLOCK.format(order.getDeliveredAt().atZone(properties.zone()));
        String payment = order.effectivePaymentChoice() == PaymentChoice.CASH
                ? rupees(order.getAmount()) + " paid to the agent."
                : "Your payment has been released from escrow to the seller.";
        save(order, NotificationType.DELIVERED, "Delivered — " + item(order),
                "Delivered" + at + ". " + payment);
    }

    public void paymentFailed(Order order) {
        save(order, NotificationType.PAYMENT_FAILED, "Payment failed — your slot was released",
                "We couldn't take " + rupees(order.getAmount()) + " for " + item(order)
                        + ". Try again, or choose to pay on delivery.");
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(String customerId, Instant after) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId is required");
        }
        PageRequest page = PageRequest.of(0, MAX_RESULTS);
        List<Notification> found = after == null
                ? notificationRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, page)
                : notificationRepository.findByCustomerIdAndCreatedAtAfterOrderByCreatedAtDesc(customerId, after, page);
        return found.stream().map(NotificationResponse::of).toList();
    }

    @Transactional
    public void markRead(String id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> ApiException.notificationNotFound(id));
        notification.markRead();
    }

    /** "Sun 29 Nov, 6–7 PM", or just the window when the order has no date. */
    String when(Order order) {
        String window = window(order.getStartHour(), order.getEndHour());
        return order.getSlotDate() == null ? window : DAY.format(order.getSlotDate()) + ", " + window;
    }

    /** "6–7 PM", "11 AM–12 PM". */
    static String window(Integer startHour, Integer endHour) {
        if (startHour == null) {
            return "your chosen slot";
        }
        int end = endHour != null ? endHour : startHour + 1;
        String startHalf = startHour < 12 ? "AM" : "PM";
        String endHalf = end % 24 < 12 ? "AM" : "PM";
        if (startHalf.equals(endHalf)) {
            return hour12(startHour) + "–" + hour12(end) + " " + endHalf;
        }
        return hour12(startHour) + " " + startHalf + "–" + hour12(end) + " " + endHalf;
    }

    /** "₹2,499", "₹1,23,450.50" (Indian digit grouping). */
    static String rupees(BigDecimal amount) {
        // The JDK's en-IN format groups in thousands, so the lakh grouping (last three, then pairs) is done here.
        BigDecimal scaled = amount.abs().setScale(2, RoundingMode.HALF_UP);
        String digits = scaled.toBigInteger().toString();
        StringBuilder grouped = new StringBuilder();
        int head = digits.length() - 3;
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && i <= head && (head - i) % 2 == 0) {
                grouped.append(',');
            }
            grouped.append(digits.charAt(i));
        }
        int paise = scaled.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        String fraction = paise == 0 ? "" : String.format(".%02d", paise);
        return (amount.signum() < 0 ? "-₹" : "₹") + grouped + fraction;
    }

    private static int hour12(int hour) {
        int h = hour % 12;
        return h == 0 ? 12 : h;
    }

    private static String item(Order order) {
        return order.getItemName() != null ? order.getItemName() : "your order";
    }

    private void save(Order order, NotificationType type, String title, String body) {
        notificationRepository.save(new Notification(order.getCustomerId(), order.getId(), type, title, body));
        log.info("Notification {} created for orderId={} customerId={}", type, order.getId(), order.getCustomerId());
    }
}
