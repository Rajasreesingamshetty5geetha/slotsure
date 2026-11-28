package com.slotsure.order.service;

import com.slotsure.order.config.TopicProperties;
import com.slotsure.order.dto.CreateOrderRequest;
import com.slotsure.order.dto.CreateOrderResponse;
import com.slotsure.order.dto.OrderResponse;
import com.slotsure.order.entity.Order;
import com.slotsure.order.entity.OrderStatus;
import com.slotsure.order.entity.PaymentChoice;
import com.slotsure.order.entity.Purchase;
import com.slotsure.order.entity.SagaInstance;
import com.slotsure.order.entity.SagaStep;
import com.slotsure.order.event.PaymentRequestedPayload;
import com.slotsure.order.event.SlotConfirmPayload;
import com.slotsure.order.exception.ApiException;
import com.slotsure.order.exception.OrderNotFoundException;
import com.slotsure.order.repository.OrderRepository;
import com.slotsure.order.repository.PurchaseRepository;
import com.slotsure.order.repository.SagaInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final PurchaseRepository purchaseRepository;
    private final SagaInstanceRepository sagaRepository;
    private final EventPublisher eventPublisher;
    private final TopicProperties topics;

    /**
     * Books a slot for a purchase (CONTRACT V2.3). The amount and payment mode come from the purchase.
     * ONLINE takes payment first (payment.requested); PREPAID and CASH go straight to slot.confirm.
     */
    @Transactional
    public CreateOrderResponse createOrder(CreateOrderRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key must not be blank");
        }
        Optional<Order> existing = orderRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            Order order = existing.get();
            log.info("Idempotent replay: returning existing orderId={} for Idempotency-Key={}", order.getId(), idempotencyKey);
            return toCreateResponse(order);
        }
        validate(request);

        Purchase purchase = purchaseRepository.findByIdForUpdate(request.purchaseId())
                .filter(found -> found.getCustomerId().equals(request.customerId()))
                .orElseThrow(() -> ApiException.purchaseNotFound(request.purchaseId()));
        PaymentChoice choice = request.paymentChoice();
        if (!purchase.getPaymentMode().allows(choice)) {
            throw ApiException.invalidPaymentChoice("A " + purchase.getPaymentMode() + " purchase cannot be booked with "
                    + choice + (choice == PaymentChoice.PREPAID ? "; choose ONLINE or CASH" : "; use PREPAID"));
        }
        orderRepository.findFirstByPurchaseIdAndStatusIn(purchase.getId(), OrderStatus.LIVE).ifPresent(live -> {
            throw ApiException.alreadyScheduled(purchase.getId(), live.getId());
        });

        Order order = new Order(request.customerId(), request.holdId(), purchase.getAmount(), idempotencyKey);
        order.setPurchaseId(purchase.getId());
        order.setItemName(purchase.getItemName());
        order.setPaymentMode(purchase.getPaymentMode());
        order.setPaymentChoice(choice);
        order.setLat(request.lat());
        order.setLng(request.lng());
        order.setAddress(request.address());
        order.setAreaName(request.areaName());
        order.setSlotDate(request.slotDate());
        order.setStartHour(request.startHour());
        order.setEndHour(request.endHour() != null || request.startHour() == null ? request.endHour() : request.startHour() + 1);
        orderRepository.save(order);
        purchase.attachOrder(order.getId());

        // DUAL WRITE: the order row and the first saga message are two separate writes. The message is
        // sent only after this transaction commits; a crash in between stalls the saga. Replace with an outbox.
        if (choice.paysAtBooking()) {
            sagaRepository.save(new SagaInstance(order.getId(), SagaStep.PAYMENT_REQUESTED));
            eventPublisher.publishAfterCommit(topics.paymentRequested(), order.getId(),
                    new PaymentRequestedPayload(order.getId(), order.getCustomerId(), order.getAmount()));
        } else {
            sagaRepository.save(new SagaInstance(order.getId(), SagaStep.SLOT_CONFIRMING));
            eventPublisher.publishAfterCommit(topics.slotConfirm(), order.getId(),
                    new SlotConfirmPayload(order.getId(), order.getHoldId()));
        }
        log.info("Order created orderId={} purchaseId={} holdId={} amount={} paymentChoice={} status=PENDING",
                order.getId(), purchase.getId(), order.getHoldId(), order.getAmount(), choice);
        return toCreateResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(String orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        SagaInstance saga = sagaRepository.findById(orderId).orElse(null);
        return OrderResponse.of(order, saga == null ? null : saga.getStep());
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listForCustomer(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId is required");
        }
        return withSagaSteps(orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId));
    }

    /** Orders whose slot is on the date, newest first (admin). */
    @Transactional(readOnly = true)
    public List<OrderResponse> listForDate(LocalDate date) {
        return withSagaSteps(orderRepository.findBySlotDateOrderByCreatedAtDesc(date));
    }

    /** One extra query for all saga steps instead of one per order. */
    private List<OrderResponse> withSagaSteps(List<Order> orders) {
        Map<String, SagaStep> steps = sagaRepository.findAllById(orders.stream().map(Order::getId).toList()).stream()
                .collect(Collectors.toMap(SagaInstance::getOrderId, SagaInstance::getStep, (a, b) -> a));
        return orders.stream().map(order -> OrderResponse.of(order, steps.get(order.getId()))).toList();
    }

    private static CreateOrderResponse toCreateResponse(Order order) {
        return new CreateOrderResponse(order.getId(), order.getStatus(), order.getPaymentMode(), order.getPaymentChoice());
    }

    private static void validate(CreateOrderRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        requireText(request.customerId(), "customerId");
        requireText(request.purchaseId(), "purchaseId");
        requireText(request.holdId(), "holdId");
        if (request.paymentChoice() == null) {
            throw ApiException.invalidPaymentChoice("paymentChoice is required: PREPAID, ONLINE or CASH");
        }
        if (request.startHour() != null && (request.startHour() < 0 || request.startHour() > 23)) {
            throw new IllegalArgumentException("startHour must be between 0 and 23");
        }
        if (request.startHour() != null && request.endHour() != null && request.endHour() <= request.startHour()) {
            throw new IllegalArgumentException("endHour must be after startHour");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
