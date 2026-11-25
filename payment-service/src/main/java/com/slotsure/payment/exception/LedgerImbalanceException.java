package com.slotsure.payment.exception;

/**
 * Thrown when a ledger transaction is empty, has a negative amount, or total debit != total credit.
 */
public class LedgerImbalanceException extends RuntimeException {

    public LedgerImbalanceException(String message) {
        super(message);
    }
}
