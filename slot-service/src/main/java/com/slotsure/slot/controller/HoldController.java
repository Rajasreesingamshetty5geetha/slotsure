package com.slotsure.slot.controller;

import com.slotsure.slot.dto.HoldRequest;
import com.slotsure.slot.dto.HoldResponse;
import com.slotsure.slot.dto.HoldStatusResponse;
import com.slotsure.slot.service.HoldService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/holds")
@RequiredArgsConstructor
public class HoldController {

    private final HoldService holdService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HoldResponse createHold(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                   @RequestBody HoldRequest request) {
        return holdService.createHold(idempotencyKey, request);
    }

    @GetMapping("/{holdId}")
    public HoldStatusResponse getHold(@PathVariable String holdId) {
        return holdService.getHold(holdId);
    }
}
