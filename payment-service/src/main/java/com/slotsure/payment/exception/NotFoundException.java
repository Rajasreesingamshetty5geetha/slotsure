package com.slotsure.payment.exception;

import lombok.Getter;

/**
 * Resource not found; code goes into the error JSON (e.g. PAYMENT_NOT_FOUND).
 */
@Getter
public class NotFoundException extends RuntimeException {

    private final String code;

    public NotFoundException(String code, String message) {
        super(message);
        this.code = code;
    }
}
