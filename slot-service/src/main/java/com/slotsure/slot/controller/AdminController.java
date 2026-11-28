package com.slotsure.slot.controller;

import com.slotsure.slot.dto.AdminSlotSummary;
import com.slotsure.slot.service.SlotService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final SlotService slotService;

    @GetMapping("/slots")
    public AdminSlotSummary getSlotSummary(@RequestParam(required = false)
                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return slotService.adminSummary(date != null ? date : slotService.today());
    }
}
