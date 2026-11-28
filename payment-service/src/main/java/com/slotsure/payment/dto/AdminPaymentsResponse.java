package com.slotsure.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * GET /api/admin/payments (CONTRACT V2.9) for one day in the configured zone.
 * prepaidCaptured: money taken before delivery (checkout and online). escrowHeld: escrow balance at the
 * end of the day (now, for today). ledgerDifference: whole-ledger debit minus credit, always 0.
 */
public record AdminPaymentsResponse(LocalDate date,
                                    BigDecimal prepaidCaptured,
                                    BigDecimal escrowHeld,
                                    BigDecimal escrowReleased,
                                    BigDecimal codCollected,
                                    BigDecimal refunds,
                                    BigDecimal ledgerDifference) {
}
