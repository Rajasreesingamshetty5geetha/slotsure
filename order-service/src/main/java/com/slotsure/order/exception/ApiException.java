package com.slotsure.order.exception;

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

    public static ApiException purchaseNotFound(String purchaseId) {
        return new ApiException(HttpStatus.NOT_FOUND, "PURCHASE_NOT_FOUND", "Purchase " + purchaseId + " not found");
    }

    public static ApiException alreadyScheduled(String purchaseId, String orderId) {
        return new ApiException(HttpStatus.CONFLICT, "ALREADY_SCHEDULED",
                "Purchase " + purchaseId + " already has order " + orderId);
    }

    public static ApiException invalidPaymentChoice(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_CHOICE", message);
    }

    public static ApiException notificationNotFound(String id) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "Notification " + id + " not found");
    }
}
