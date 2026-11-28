package com.slotsure.slot.controller;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.slotsure.slot.dto.AdminSlotSummary;
import com.slotsure.slot.exception.GlobalExceptionHandler;
import com.slotsure.slot.service.SlotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminControllerTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 28);

    private SlotService slotService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        slotService = mock(SlotService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminController(slotService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .build()))
                .build();
    }

    @Test
    void returnsTheContractShape() throws Exception {
        when(slotService.adminSummary(DATE)).thenReturn(new AdminSlotSummary(DATE, 280, 27, 9.6,
                List.of(new AdminSlotSummary.AreaLoad("Koramangala", 140, 21)),
                List.of(new AdminSlotSummary.BusySlot("Koramangala", 19, 20, 6, 10))));

        mockMvc.perform(get("/api/admin/slots").param("date", "2026-11-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-11-28"))
                .andExpect(jsonPath("$.totalCapacity").value(280))
                .andExpect(jsonPath("$.totalBooked").value(27))
                .andExpect(jsonPath("$.utilisationPct").value(9.6))
                .andExpect(jsonPath("$.byArea[0].areaName").value("Koramangala"))
                .andExpect(jsonPath("$.byArea[0].capacity").value(140))
                .andExpect(jsonPath("$.byArea[0].booked").value(21))
                .andExpect(jsonPath("$.busiest[0].areaName").value("Koramangala"))
                .andExpect(jsonPath("$.busiest[0].startHour").value(19))
                .andExpect(jsonPath("$.busiest[0].endHour").value(20))
                .andExpect(jsonPath("$.busiest[0].booked").value(6))
                .andExpect(jsonPath("$.busiest[0].capacity").value(10));
    }

    @Test
    void defaultsToTodayWhenNoDateIsGiven() throws Exception {
        when(slotService.today()).thenReturn(DATE);
        when(slotService.adminSummary(DATE)).thenReturn(new AdminSlotSummary(DATE, 0, 0, 0.0, List.of(), List.of()));

        mockMvc.perform(get("/api/admin/slots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-11-28"));
    }

    @Test
    void rejectsAMalformedDate() throws Exception {
        mockMvc.perform(get("/api/admin/slots").param("date", "28-11-2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void unknownPathIsAQuiet404() throws Exception {
        mockMvc.perform(get("/api/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No endpoint GET /api/nope"));
    }

    @Test
    void wrongMethodOnAKnownPathIsA405WithAllowHeader() throws Exception {
        mockMvc.perform(post("/api/admin/slots"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").value("Method POST is not allowed on /api/admin/slots"));
    }

    @Test
    void missingStaticResourceMapsToNotFound() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/favicon.ico");

        ResponseEntity<?> response = new GlobalExceptionHandler().handleNotFound(request);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("code", "NOT_FOUND");
    }
}
