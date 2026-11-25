package com.slotsure.order.exception;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(String orderId) {
        super("Order " + orderId + " not found");
    }
}
