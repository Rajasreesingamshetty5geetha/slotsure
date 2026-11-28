package com.slotsure.order.controller;

import com.slotsure.order.dto.PurchaseResponse;
import com.slotsure.order.service.PurchaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/purchases")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;

    @GetMapping
    public List<PurchaseResponse> listPurchases(@RequestParam String customerId) {
        return purchaseService.listForCustomer(customerId);
    }
}
