package com.slotsure.dispatch.controller;

import com.slotsure.dispatch.dto.CompletionResponse;
import com.slotsure.dispatch.service.AssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final AssignmentService assignmentService;

    @PostMapping("/{orderId}/complete")
    public CompletionResponse complete(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                       @PathVariable String orderId) {
        return assignmentService.complete(orderId, idempotencyKey);
    }
}
