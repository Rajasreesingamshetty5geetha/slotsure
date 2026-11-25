package com.slotsure.payment.controller;

import com.slotsure.payment.dto.LedgerCheckResponse;
import com.slotsure.payment.dto.LedgerEntryResponse;
import com.slotsure.payment.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ledger")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;

    @GetMapping("/check")
    public LedgerCheckResponse check() {
        return ledgerService.check();
    }

    @GetMapping("/{orderId}")
    public List<LedgerEntryResponse> getEntries(@PathVariable String orderId) {
        return ledgerService.entriesForOrder(orderId);
    }
}
