package com.slotsure.payment.service;

import com.slotsure.payment.config.TopicProperties;
import com.slotsure.payment.dto.LedgerLine;
import com.slotsure.payment.dto.PaymentAttemptResponse;
import com.slotsure.payment.entity.LedgerEntry;
import com.slotsure.payment.entity.PaymentAttempt;
import com.slotsure.payment.entity.PaymentStatus;
import com.slotsure.payment.entity.ProcessedEvent;
import com.slotsure.payment.event.EventPublisher;
import com.slotsure.payment.event.PaymentCapturedEvent;
import com.slotsure.payment.event.PaymentFailedEvent;
import com.slotsure.payment.event.PaymentRequestedEvent;
import com.slotsure.payment.event.RefundCompletedEvent;
import com.slotsure.payment.event.RefundRequestedEvent;
import com.slotsure.payment.exception.NotFoundException;
import com.slotsure.payment.repository.LedgerEntryRepository;
import com.slotsure.payment.repository.PaymentAttemptRepository;
import com.slotsure.payment.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles payment.requested and refund.requested. Each handler runs in one database transaction
 * that also records the eventId in processed_events; outgoing events are sent after commit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    static final String PAYMENT_CAPTURED = "payment.captured";
    static final String PAYMENT_FAILED = "payment.failed";
    static final String REFUND_COMPLETED = "refund.completed";

    private final GatewayRouter gatewayRouter;
    private final LedgerService ledgerService;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final EventPublisher eventPublisher;
    private final TopicProperties topics;

    @Transactional
    public void handlePaymentRequested(String eventId, PaymentRequestedEvent request) {
        String orderId = request.orderId();
        if (!markProcessed(eventId, orderId, "payment.requested")) {
            return;
        }

        Optional<PaymentAttempt> existing =
                paymentAttemptRepository.findFirstByOrderIdAndStatus(orderId, PaymentStatus.SUCCESS);
        if (existing.isPresent()) {
            PaymentAttempt paid = existing.get();
            log.info("Order already paid, re-publishing payment.captured orderId={} paymentId={}", orderId, paid.getId());
            publishCaptured(paid);
            return;
        }

        BigDecimal amount = LedgerService.money(request.amount());
        if (amount.signum() <= 0) {
            log.warn("Rejecting payment with non-positive amount orderId={} amount={}", orderId, amount);
            eventPublisher.publish(topics.paymentFailed(), PAYMENT_FAILED, orderId,
                    new PaymentFailedEvent(orderId, "amount must be greater than 0"));
            return;
        }

        log.info("Charging orderId={} customerId={} amount={}", orderId, request.customerId(), amount);
        GatewayRouter.Outcome outcome = gatewayRouter.charge(orderId, amount);

        PaymentAttempt successful = null;
        for (GatewayRouter.Call call : outcome.calls()) {
            PaymentAttempt attempt = paymentAttemptRepository.save(PaymentAttempt.builder()
                    .orderId(orderId)
                    .customerId(request.customerId())
                    .gateway(call.gateway())
                    .amount(amount)
                    .status(call.result().success() ? PaymentStatus.SUCCESS : PaymentStatus.FAILED)
                    .failureReason(call.result().error())
                    .createdAt(Instant.now())
                    .build());
            log.info("Saved payment attempt orderId={} paymentId={} gateway={} status={}",
                    orderId, attempt.getId(), attempt.getGateway(), attempt.getStatus());
            if (attempt.getStatus() == PaymentStatus.SUCCESS) {
                successful = attempt;
            }
        }

        if (successful == null) {
            String reason = outcome.combinedError();
            log.warn("Payment failed on all gateways orderId={} reason={}", orderId, reason);
            eventPublisher.publish(topics.paymentFailed(), PAYMENT_FAILED, orderId,
                    new PaymentFailedEvent(orderId, reason));
            return;
        }

        ledgerService.post(UUID.randomUUID().toString(), orderId, List.of(
                LedgerLine.debit(LedgerEntry.customerAccount(request.customerId()), amount),
                LedgerLine.credit(LedgerEntry.ESCROW, amount)));
        publishCaptured(successful);
    }

    @Transactional
    public void handleRefundRequested(String eventId, RefundRequestedEvent request) {
        String orderId = request.orderId();
        if (!markProcessed(eventId, orderId, "refund.requested")) {
            return;
        }

        Optional<PaymentAttempt> paidAttempt =
                paymentAttemptRepository.findFirstByOrderIdAndStatus(orderId, PaymentStatus.SUCCESS);
        if (paidAttempt.isEmpty()) {
            log.warn("Refund requested but no successful payment exists orderId={} reason={}", orderId, request.reason());
            publishRefundCompleted(orderId, BigDecimal.ZERO);
            return;
        }

        PaymentAttempt paid = paidAttempt.get();
        String wallet = LedgerEntry.walletAccount(paid.getCustomerId());
        BigDecimal amount = LedgerService.money(paid.getAmount());

        if (ledgerEntryRepository.existsByOrderIdAndAccount(orderId, wallet)) {
            log.info("Order already refunded, re-publishing refund.completed orderId={}", orderId);
            publishRefundCompleted(orderId, amount);
            return;
        }
        if (request.amount() != null && LedgerService.money(request.amount()).compareTo(amount) != 0) {
            log.warn("Refund amount {} differs from captured amount {}, refunding captured amount orderId={}",
                    request.amount(), amount, orderId);
        }

        ledgerService.post(UUID.randomUUID().toString(), orderId, List.of(
                LedgerLine.debit(LedgerEntry.ESCROW, amount),
                LedgerLine.credit(wallet, amount)));
        log.info("Refunded orderId={} amount={} to {} reason={}", orderId, amount, wallet, request.reason());
        publishRefundCompleted(orderId, amount);
    }

    @Transactional(readOnly = true)
    public List<PaymentAttemptResponse> attemptsForOrder(String orderId) {
        List<PaymentAttempt> attempts = paymentAttemptRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
        if (attempts.isEmpty()) {
            throw new NotFoundException("PAYMENT_NOT_FOUND", "No payment attempts for order " + orderId);
        }
        return attempts.stream().map(PaymentAttemptResponse::from).toList();
    }

    /** Returns false (and logs) when the event was already handled. */
    private boolean markProcessed(String eventId, String orderId, String eventType) {
        if (processedEventRepository.existsById(eventId)) {
            log.info("Skipping duplicate {} orderId={} eventId={}", eventType, orderId, eventId);
            return false;
        }
        processedEventRepository.save(new ProcessedEvent(eventId, Instant.now()));
        return true;
    }

    private void publishCaptured(PaymentAttempt paid) {
        eventPublisher.publish(topics.paymentCaptured(), PAYMENT_CAPTURED, paid.getOrderId(),
                new PaymentCapturedEvent(paid.getOrderId(), paid.getId(), paid.getGateway(),
                        LedgerService.money(paid.getAmount())));
    }

    private void publishRefundCompleted(String orderId, BigDecimal amount) {
        eventPublisher.publish(topics.refundCompleted(), REFUND_COMPLETED, orderId,
                new RefundCompletedEvent(orderId, LedgerService.money(amount)));
    }
}
