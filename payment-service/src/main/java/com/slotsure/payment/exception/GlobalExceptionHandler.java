package com.slotsure.payment.exception;

import com.slotsure.payment.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * The single advice for this service. Error JSON: {"code": "...", "message": "..."}.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e.getCode(), e.getMessage());
    }

    /** An unknown path is a client mistake, not a server error: plain 404, no stack trace. */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ErrorResponse> handleNoEndpoint(HttpServletRequest request) {
        log.debug("No endpoint for {} {}", request.getMethod(), request.getRequestURI());
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "No endpoint " + request.getMethod() + " " + request.getRequestURI());
    }

    /** A known path with the wrong HTTP method: 405 with the Allow header, no stack trace. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e,
                                                                HttpServletRequest request) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        if (e.getSupportedHttpMethods() != null) {
            response.allow(e.getSupportedHttpMethods().toArray(new HttpMethod[0]));
        }
        return response.body(new ErrorResponse("METHOD_NOT_ALLOWED",
                "Method " + e.getMethod() + " is not allowed on " + request.getRequestURI()));
    }

    @ExceptionHandler(LedgerImbalanceException.class)
    public ResponseEntity<ErrorResponse> handleLedgerImbalance(LedgerImbalanceException e) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "LEDGER_UNBALANCED", e.getMessage());
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingRequestHeaderException.class,
            MissingServletRequestParameterException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error");
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }
}
