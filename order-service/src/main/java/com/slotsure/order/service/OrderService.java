package com.slotsure.order.service;

import com.slotsure.order.config.TopicProperties;
import com.slotsure.order.dto.CreateOrderRequest;
import com.slotsure.order.dto.CreateOrderResponse;
import com.slotsure.order.dto.OrderResponse;
import com.slotsure.order.entity.Order;
import com.slotsure.order.entity.SagaInstance;
import com.slotsure.order.event.PaymentRequestedPayload;
import com.slotsure.order.exception.OrderNotFoundException;
import com.slotsure.order.repository.OrderRepository;
import com.slotsure.order.repository.SagaInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final SagaInstanceRepository sagaRepository;
    private final EventPublisher eventPublisher;
    private final TopicProperties topics;

    @Transactional
    public CreateOrderResponse createOrder(CreateOrderRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key must not be blank");
        }
        Optional<Order> existing = orderRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            Order order = existing.get();
            log.info("Idempotent replay: returning existing orderId={} for Idempotency-Key={}", order.getId(), idempotencyKey);
            return new CreateOrderResponse(order.getId(), order.getStatus());
        }
        validate(request);

        Order order = orderRepository.save(
                new Order(request.customerId(), request.holdId(), request.amount(), idempotencyKey));
        sagaRepository.save(new SagaInstance(order.getId()));
        log.info("Order created orderId={} holdId={} amount={} status={} sagaStep=PAYMENT_REQUESTED",
                order.getId(), order.getHoldId(), order.getAmount(), order.getStatus());

        // DUAL WRITE: the order row and the payment.requested message are two separate writes.
        // The message is sent only after this transaction commits; if the app crashes in between,
        // the saga is stuck in PAYMENT_REQUESTED. Replace with a Transactional Outbox.
        log.warn("DUAL WRITE: payment.requested for orderId={} will be sent after commit; replace with Transactional Outbox",
                order.getId());
        eventPublisher.publishAfterCommit(topics.paymentRequested(), order.getId(),
                new PaymentRequestedPayload(order.getId(), order.getCustomerId(), order.getAmount()));

        return new CreateOrderResponse(order.getId(), order.getStatus());
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(String orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        SagaInstance saga = sagaRepository.findById(orderId).orElse(null);
        return new OrderResponse(order.getId(), order.getHoldId(), order.getAmount(), order.getStatus(),
                saga == null ? null : saga.getStep(), order.getUpdatedAt());
    }

    private void validate(CreateOrderRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (request.customerId() == null || request.customerId().isBlank()) {
            throw new IllegalArgumentException("customerId is required");
        }
        if (request.holdId() == null || request.holdId().isBlank()) {
            throw new IllegalArgumentException("holdId is required");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be greater than 0");
        }
    }
}
