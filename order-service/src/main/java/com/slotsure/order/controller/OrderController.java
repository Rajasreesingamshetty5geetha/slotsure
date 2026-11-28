package com.slotsure.order.controller;

import com.slotsure.order.dto.CreateOrderRequest;
import com.slotsure.order.dto.CreateOrderResponse;
import com.slotsure.order.dto.OrderResponse;
import com.slotsure.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** A repeated Idempotency-Key returns the original order with the same 201 response. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateOrderResponse createOrder(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                           @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request, idempotencyKey);
    }

    @GetMapping
    public List<OrderResponse> listOrders(@RequestParam String customerId) {
        return orderService.listForCustomer(customerId);
    }

    @GetMapping("/{orderId}")
    public OrderResponse getOrder(@PathVariable String orderId) {
        return orderService.getOrder(orderId);
    }
}
