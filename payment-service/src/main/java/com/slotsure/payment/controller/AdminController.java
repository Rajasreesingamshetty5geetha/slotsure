package com.slotsure.payment.controller;

import com.slotsure.payment.dto.AdminPaymentsResponse;
import com.slotsure.payment.service.AdminService;
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

    private final AdminService adminService;

    /** Without a date, today in the configured zone. */
    @GetMapping("/payments")
    public AdminPaymentsResponse getPayments(@RequestParam(required = false)
                                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return adminService.summary(date != null ? date : adminService.today());
    }
}
