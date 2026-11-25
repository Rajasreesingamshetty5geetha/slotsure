package com.slotsure.slot.exception;

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

    public static ApiException slotFull(String slotId) {
        return new ApiException(HttpStatus.CONFLICT, "SLOT_FULL", "Slot " + slotId + " has no places left");
    }

    public static ApiException slotNotFound(String slotId) {
        return new ApiException(HttpStatus.NOT_FOUND, "SLOT_NOT_FOUND", "Slot " + slotId + " does not exist");
    }

    public static ApiException holdNotFound(String holdId) {
        return new ApiException(HttpStatus.NOT_FOUND, "HOLD_NOT_FOUND", "Hold " + holdId + " does not exist");
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }
}
