package com.slotsure.dispatch.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** A business error that maps straight to the contract's error JSON. */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException notAssigned(String orderId) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_ASSIGNED", "Order " + orderId + " is not assigned to an agent");
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }
}
