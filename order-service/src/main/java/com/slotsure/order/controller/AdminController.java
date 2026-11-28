package com.slotsure.order.controller;

import com.slotsure.order.dto.AdminSummaryResponse;
import com.slotsure.order.dto.OrderResponse;
import com.slotsure.order.service.AdminService;
import com.slotsure.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Dates are slot dates; without one, today in the configured zone. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final OrderService orderService;

    @GetMapping("/summary")
    public AdminSummaryResponse getSummary(@RequestParam(required = false)
                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return adminService.summary(date != null ? date : adminService.today());
    }

    @GetMapping("/orders")
    public List<OrderResponse> getOrders(@RequestParam(required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return orderService.listForDate(date != null ? date : adminService.today());
    }
}
