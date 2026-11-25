package com.slotsure.order.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotsure.order.config.TopicProperties;
import com.slotsure.order.entity.Order;
import com.slotsure.order.entity.SagaInstance;
import com.slotsure.order.event.PaymentRequestedPayload;
import com.slotsure.order.exception.GlobalExceptionHandler;
import com.slotsure.order.repository.OrderRepository;
import com.slotsure.order.repository.SagaInstanceRepository;
import com.slotsure.order.service.EventPublisher;
import com.slotsure.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Controller + real OrderService; repositories are in-memory Mockito stubs, publisher is a mock. */
class OrderControllerTest {

    private static final TopicProperties TOPICS = new TopicProperties(
            "payment.requested", "payment.captured", "payment.failed",
            "slot.confirm", "slot.release", "slot.confirmed", "slot.confirm.failed",
            "refund.requested", "refund.completed");
    private static final String BODY = """
            {"customerId": "customer-1", "holdId": "6f1c2a7e-0000-4000-8000-000000000001", "amount": 25.5}
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final SagaInstanceRepository sagaRepository = mock(SagaInstanceRepository.class);
    private final EventPublisher eventPublisher = mock(EventPublisher.class);
    private final Map<String, Order> ordersByKey = new HashMap<>();
    private final Map<String, Order> ordersById = new HashMap<>();
    private final Map<String, SagaInstance> sagasById = new HashMap<>();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        OrderService orderService = new OrderService(orderRepository, sagaRepository, eventPublisher, TOPICS);
        mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(orderService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        when(orderRepository.findByIdempotencyKey(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(ordersByKey.get(inv.<String>getArgument(0))));
        when(orderRepository.findById(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(ordersById.get(inv.<String>getArgument(0))));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order order = inv.getArgument(0);
            ordersByKey.put(order.getIdempotencyKey(), order);
            ordersById.put(order.getId(), order);
            return order;
        });
        when(sagaRepository.findById(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(sagasById.get(inv.<String>getArgument(0))));
        when(sagaRepository.save(any(SagaInstance.class))).thenAnswer(inv -> {
            SagaInstance saga = inv.getArgument(0);
            sagasById.put(saga.getOrderId(), saga);
            return saga;
        });
    }

    @Test
    void createsPendingOrderAndRequestsPayment() throws Exception {
        String orderId = postOrder("key-A");

        Order order = ordersById.get(orderId);
        assertThat(order.getAmount()).isEqualTo(new BigDecimal("25.50"));
        assertThat(sagasById).containsKey(orderId);
        verify(eventPublisher).publishAfterCommit("payment.requested", orderId,
                new PaymentRequestedPayload(orderId, "customer-1", new BigDecimal("25.50")));
    }

    @Test
    void sameIdempotencyKeyTwiceReturnsSameOrder() throws Exception {
        String first = postOrder("key-B");
        String second = postOrder("key-B");

        assertThat(second).isEqualTo(first);
        verify(orderRepository, times(1)).save(any(Order.class));
        verify(eventPublisher, times(1)).publishAfterCommit(eq("payment.requested"), anyString(), any());
    }

    @Test
    void differentIdempotencyKeysCreateDifferentOrders() throws Exception {
        assertThat(postOrder("key-C")).isNotEqualTo(postOrder("key-D"));
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_HEADER"));
    }

    @Test
    void invalidAmountIsRejected() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "key-E")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\": \"c\", \"holdId\": \"h\", \"amount\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void getOrderReturnsStatusAndSagaStep() throws Exception {
        String orderId = postOrder("key-F");

        mockMvc.perform(get("/api/orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.sagaStep").value("PAYMENT_REQUESTED"))
                .andExpect(jsonPath("$.amount").value(25.50))
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(content().string(containsString("\"amount\":25.50")));
    }

    @Test
    void unknownOrderReturns404() throws Exception {
        mockMvc.perform(get("/api/orders/{orderId}", "does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    private String postOrder(String idempotencyKey) throws Exception {
        String response = mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.get("orderId").asText();
    }
}
