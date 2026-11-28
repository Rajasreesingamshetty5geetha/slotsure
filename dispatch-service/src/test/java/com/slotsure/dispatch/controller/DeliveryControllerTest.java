package com.slotsure.dispatch.controller;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.slotsure.dispatch.dto.CompletionResponse;
import com.slotsure.dispatch.exception.ApiException;
import com.slotsure.dispatch.exception.GlobalExceptionHandler;
import com.slotsure.dispatch.service.AssignmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeliveryControllerTest {

    private AssignmentService assignmentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        assignmentService = mock(AssignmentService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DeliveryController(assignmentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
                .build();
    }

    @Test
    void completeReturnsTheDeliveryTime() throws Exception {
        when(assignmentService.complete("order-1", "key-1"))
                .thenReturn(new CompletionResponse("order-1", Instant.parse("2026-11-29T12:52:00Z")));

        mockMvc.perform(post("/api/deliveries/order-1/complete").header("Idempotency-Key", "key-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("order-1"))
                .andExpect(jsonPath("$.deliveredAt").value("2026-11-29T12:52:00Z"));
    }

    @Test
    void anUnassignedOrderIs404NotAssigned() throws Exception {
        when(assignmentService.complete(eq("nope"), eq("key-1"))).thenThrow(ApiException.notAssigned("nope"));

        mockMvc.perform(post("/api/deliveries/nope/complete").header("Idempotency-Key", "key-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_ASSIGNED"));
    }

    @Test
    void aMissingIdempotencyKeyIs400() throws Exception {
        when(assignmentService.complete(eq("order-1"), isNull()))
                .thenThrow(ApiException.badRequest("Idempotency-Key header is required"));

        mockMvc.perform(post("/api/deliveries/order-1/complete"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void unknownPathAndWrongMethodAreQuiet() throws Exception {
        mockMvc.perform(get("/api/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(get("/api/deliveries/order-1/complete"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }
}
