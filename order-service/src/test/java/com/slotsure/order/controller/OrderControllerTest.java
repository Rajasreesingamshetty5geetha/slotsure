package com.slotsure.order.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.slotsure.order.InMemoryStore;
import com.slotsure.order.entity.Order;
import com.slotsure.order.entity.OrderStatus;
import com.slotsure.order.entity.PaymentMode;
import com.slotsure.order.entity.SagaStep;
import com.slotsure.order.event.PaymentRequestedPayload;
import com.slotsure.order.event.SlotConfirmPayload;
import com.slotsure.order.exception.GlobalExceptionHandler;
import com.slotsure.order.service.EventPublisher;
import com.slotsure.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Controller + real OrderService over in-memory repositories; the Kafka publisher is a mock. */
class OrderControllerTest {

    private static final String CUSTOMER = "6f1c2a10-3b7d-4e2a-9c11-0a1b2c3d4e01";
    private static final String HOLD = "6f1c2a7e-0000-4000-8000-000000000001";

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private final InMemoryStore store = new InMemoryStore();
    private final EventPublisher eventPublisher = mock(EventPublisher.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        OrderService orderService = new OrderService(store.orderRepository, store.purchaseRepository,
                store.sagaRepository, eventPublisher, InMemoryStore.TOPICS);
        mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(orderService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
        store.addPurchase("p-prepaid", CUSTOMER, "Wireless Headphones", "2499", PaymentMode.PREPAID);
        store.addPurchase("p-cod", CUSTOMER, "Air Fryer 4L", "5499", PaymentMode.COD);
        store.addPurchase("p-cod-2", CUSTOMER, "Yoga Mat", "899", PaymentMode.COD);
    }

    @Test
    void prepaidSkipsPaymentAndConfirmsTheSlotStraightAway() throws Exception {
        String orderId = createOrder("key-1", "p-prepaid", "PREPAID")
                .andExpect(jsonPath("$.paymentMode").value("PREPAID"))
                .andExpect(jsonPath("$.paymentChoice").value("PREPAID"))
                .andReturn().getResponse().getContentAsString().transform(this::orderIdOf);

        Order order = store.orders.get(orderId);
        assertThat(order.getAmount()).isEqualTo(new BigDecimal("2499.00"));
        assertThat(order.getItemName()).isEqualTo("Wireless Headphones");
        assertThat(order.getSlotDate()).isEqualTo(LocalDate.of(2026, 11, 29));
        assertThat(order.getStartHour()).isEqualTo(18);
        assertThat(order.getEndHour()).isEqualTo(19);
        assertThat(store.sagas.get(orderId).getStep()).isEqualTo(SagaStep.SLOT_CONFIRMING);
        assertThat(store.purchases.get("p-prepaid").getOrderId()).isEqualTo(orderId);
        verify(eventPublisher).publishAfterCommit("slot.confirm", orderId, new SlotConfirmPayload(orderId, HOLD));
        verify(eventPublisher, never()).publishAfterCommit(eq("payment.requested"), anyString(), any());
    }

    @Test
    void cashSkipsPaymentAndConfirmsTheSlotStraightAway() throws Exception {
        String orderId = orderIdOf(createOrder("key-2", "p-cod", "CASH")
                .andExpect(jsonPath("$.paymentMode").value("COD"))
                .andExpect(jsonPath("$.paymentChoice").value("CASH"))
                .andReturn().getResponse().getContentAsString());

        assertThat(store.sagas.get(orderId).getStep()).isEqualTo(SagaStep.SLOT_CONFIRMING);
        verify(eventPublisher).publishAfterCommit("slot.confirm", orderId, new SlotConfirmPayload(orderId, HOLD));
        verify(eventPublisher, never()).publishAfterCommit(eq("payment.requested"), anyString(), any());
    }

    @Test
    void onlineRunsThePaymentSagaForThePurchaseAmount() throws Exception {
        String orderId = orderIdOf(createOrder("key-3", "p-cod", "ONLINE")
                .andReturn().getResponse().getContentAsString());

        assertThat(store.sagas.get(orderId).getStep()).isEqualTo(SagaStep.PAYMENT_REQUESTED);
        verify(eventPublisher).publishAfterCommit("payment.requested", orderId,
                new PaymentRequestedPayload(orderId, CUSTOMER, new BigDecimal("5499.00")));
        verify(eventPublisher, never()).publishAfterCommit(eq("slot.confirm"), anyString(), any());
    }

    @Test
    void anAmountSentByTheClientIsIgnored() throws Exception {
        String body = body("p-cod", "ONLINE").replace("\"customerId\"", "\"amount\": 1, \"customerId\"");
        String orderId = orderIdOf(send("key-4", body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        assertThat(store.orders.get(orderId).getAmount()).isEqualTo(new BigDecimal("5499.00"));
    }

    @Test
    void onlineOrCashForAPrepaidPurchaseIsRejected() throws Exception {
        createOrder("key-5", "p-prepaid", "ONLINE", 400).andExpect(jsonPath("$.code").value("INVALID_PAYMENT_CHOICE"));
        createOrder("key-6", "p-prepaid", "CASH", 400).andExpect(jsonPath("$.code").value("INVALID_PAYMENT_CHOICE"));
        createOrder("key-7", "p-cod", "PREPAID", 400).andExpect(jsonPath("$.code").value("INVALID_PAYMENT_CHOICE"));

        assertThat(store.orders).isEmpty();
        verify(eventPublisher, never()).publishAfterCommit(anyString(), anyString(), any());
    }

    @Test
    void missingPaymentChoiceIsRejected() throws Exception {
        send("key-8", body("p-cod", "ONLINE").replace("\"paymentChoice\": \"ONLINE\",", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_CHOICE"));
    }

    @Test
    void aSecondLiveOrderForThePurchaseIsRejected() throws Exception {
        createOrder("key-9", "p-cod", "CASH");

        createOrder("key-10", "p-cod", "ONLINE", 409).andExpect(jsonPath("$.code").value("ALREADY_SCHEDULED"));
    }

    @Test
    void aFailedOrderFreesThePurchaseForAnotherTry() throws Exception {
        String first = orderIdOf(createOrder("key-11", "p-cod", "ONLINE").andReturn().getResponse().getContentAsString());
        store.orders.get(first).moveTo(OrderStatus.FAILED);

        createOrder("key-12", "p-cod", "CASH");
    }

    @Test
    void anotherCustomersPurchaseIsNotFound() throws Exception {
        send("key-13", body("p-cod", "CASH").replace(CUSTOMER, "someone-else"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PURCHASE_NOT_FOUND"));
    }

    @Test
    void sameIdempotencyKeyTwiceReturnsTheSameOrder() throws Exception {
        String first = orderIdOf(createOrder("key-14", "p-cod", "CASH").andReturn().getResponse().getContentAsString());
        String second = orderIdOf(createOrder("key-14", "p-cod", "CASH").andReturn().getResponse().getContentAsString());

        assertThat(second).isEqualTo(first);
        verify(eventPublisher, times(1)).publishAfterCommit(eq("slot.confirm"), anyString(), any());
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body("p-cod", "CASH")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_HEADER"));
    }

    @Test
    void getOrderReturnsTheV2Shape() throws Exception {
        String orderId = orderIdOf(createOrder("key-15", "p-prepaid", "PREPAID").andReturn().getResponse().getContentAsString());

        mockMvc.perform(get("/api/orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.purchaseId").value("p-prepaid"))
                .andExpect(jsonPath("$.itemName").value("Wireless Headphones"))
                .andExpect(jsonPath("$.paymentMode").value("PREPAID"))
                .andExpect(jsonPath("$.paymentChoice").value("PREPAID"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.slotDate").value("2026-11-29"))
                .andExpect(jsonPath("$.startHour").value(18))
                .andExpect(jsonPath("$.endHour").value(19))
                .andExpect(jsonPath("$.address").value("12, 4th Cross, Koramangala"))
                .andExpect(jsonPath("$.areaName").value("Koramangala"))
                .andExpect(jsonPath("$.agentName").value(nullValue()))
                .andExpect(jsonPath("$.sagaStep").value("SLOT_CONFIRMING"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(content().string(containsString("\"amount\":2499.00")));
    }

    @Test
    void listsOnlyTheCustomersOwnOrders() throws Exception {
        String mine = orderIdOf(createOrder("key-16", "p-cod", "CASH").andReturn().getResponse().getContentAsString());
        store.addPurchase("p-other", "6f1c2a10-3b7d-4e2a-9c11-0a1b2c3d4e02", "Smart Watch", "4999", PaymentMode.PREPAID);
        send("key-17", body("p-other", "PREPAID").replace(CUSTOMER, "6f1c2a10-3b7d-4e2a-9c11-0a1b2c3d4e02"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/orders").param("customerId", CUSTOMER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].orderId").value(mine));
    }

    @Test
    void unknownOrderReturns404() throws Exception {
        mockMvc.perform(get("/api/orders/{orderId}", "does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void unknownPathIsAQuiet404() throws Exception {
        mockMvc.perform(get("/api/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private ResultActions createOrder(String key, String purchaseId, String choice) throws Exception {
        return createOrder(key, purchaseId, choice, 201).andExpect(jsonPath("$.status").value("PENDING"));
    }

    private ResultActions createOrder(String key, String purchaseId, String choice, int expectedStatus) throws Exception {
        return send(key, body(purchaseId, choice)).andExpect(status().is(expectedStatus));
    }

    private ResultActions send(String key, String body) throws Exception {
        return mockMvc.perform(post("/api/orders")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String body(String purchaseId, String choice) {
        return """
                {"customerId": "%s", "purchaseId": "%s", "holdId": "%s", "paymentChoice": "%s",
                 "lat": 12.9352, "lng": 77.6245, "address": "12, 4th Cross, Koramangala", "areaName": "Koramangala",
                 "slotDate": "2026-11-29", "startHour": 18, "endHour": 19}
                """.formatted(CUSTOMER, purchaseId, HOLD, choice);
    }

    private String orderIdOf(String response) {
        try {
            JsonNode json = objectMapper.readTree(response);
            return json.get("orderId").asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
