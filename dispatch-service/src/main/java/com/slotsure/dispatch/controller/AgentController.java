package com.slotsure.dispatch.controller;

import com.slotsure.dispatch.dto.AdminDispatchResponse;
import com.slotsure.dispatch.dto.AgentResponse;
import com.slotsure.dispatch.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Read-only agent and dispatch views; without a date, today in the configured zone. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AgentController {

    private final AdminService adminService;

    @GetMapping("/agents")
    public List<AgentResponse> getAgents(@RequestParam(required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return adminService.agents(date != null ? date : adminService.today());
    }

    @GetMapping("/admin/dispatch")
    public AdminDispatchResponse getDispatch(@RequestParam(required = false)
                                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return adminService.dispatch(date != null ? date : adminService.today());
    }
}
