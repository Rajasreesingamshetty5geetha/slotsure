package com.slotsure.slot.controller;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.slotsure.slot.dto.HoldRequest;
import com.slotsure.slot.dto.HoldResponse;
import com.slotsure.slot.exception.ApiException;
import com.slotsure.slot.exception.GlobalExceptionHandler;
import com.slotsure.slot.service.HoldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HoldControllerTest {

    private static final String BODY = "{\"slotId\":\"slot-1\",\"customerId\":\"cust-1\"}";

    private HoldService holdService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        holdService = mock(HoldService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new HoldController(holdService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .build()))
                .build();
    }

    @Test
    void returns201WithHold() throws Exception {
        when(holdService.createHold(eq("k1"), any(HoldRequest.class)))
                .thenReturn(new HoldResponse("hold-1", "slot-1", Instant.parse("2026-11-26T01:05:00Z")));

        mockMvc.perform(post("/api/holds").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.holdId").value("hold-1"))
                .andExpect(jsonPath("$.slotId").value("slot-1"))
                .andExpect(jsonPath("$.expiresAt").value("2026-11-26T01:05:00Z"));
    }

    @Test
    void returns409SlotFullAsContractErrorJson() throws Exception {
        when(holdService.createHold(eq("k1"), any(HoldRequest.class))).thenThrow(ApiException.slotFull("slot-1"));

        mockMvc.perform(post("/api/holds").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_FULL"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void returns400WhenIdempotencyKeyMissing() throws Exception {
        when(holdService.createHold(isNull(), any(HoldRequest.class)))
                .thenThrow(ApiException.badRequest("Idempotency-Key header is required"));

        mockMvc.perform(post("/api/holds").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void returns400ForUnreadableBody() throws Exception {
        mockMvc.perform(post("/api/holds").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }
}
