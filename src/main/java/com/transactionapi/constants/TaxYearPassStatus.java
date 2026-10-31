package com.transactionapi.constants;

import java.util.EnumSet;
import java.util.Set;

public enum TaxYearPassStatus {
    /** Checkout link issued; no completed Stripe checkout yet. */
    PENDING,
    /** Checkout completed with a delayed payment method that has not cleared yet. */
    PROCESSING,
    /** Payment received. The only status that unlocks the tax year. */
    PAID,
    /** Delayed payment failed. The same pass can be retried. */
    FAILED,
    /** Payment fully refunded. Terminal; a new purchase creates a new pass. */
    REFUNDED;

    public boolean canTransitionTo(TaxYearPassStatus next) {
        return allowedNext().contains(next);
    }

    private Set<TaxYearPassStatus> allowedNext() {
        return switch (this) {
            case PENDING -> EnumSet.of(PROCESSING, PAID, FAILED);
            case PROCESSING -> EnumSet.of(PAID, FAILED);
            case FAILED -> EnumSet.of(PROCESSING, PAID);
            case PAID -> EnumSet.of(REFUNDED);
            case REFUNDED -> EnumSet.noneOf(TaxYearPassStatus.class);
        };
    }
}
