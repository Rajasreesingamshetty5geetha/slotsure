package com.slotsure.payment.controller;

import com.slotsure.payment.dto.PaymentAttemptResponse;
import com.slotsure.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/{orderId}")
    public List<PaymentAttemptResponse> getPayments(@PathVariable String orderId) {
        return paymentService.attemptsForOrder(orderId);
    }
}
