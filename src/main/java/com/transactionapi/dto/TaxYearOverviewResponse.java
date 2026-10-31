package com.transactionapi.dto;

import com.transactionapi.constants.TaxYearPassStatus;
import java.util.List;

/** Free overview of the caller's tax years and their pass status, newest year first. */
public record TaxYearOverviewResponse(
        int currentTaxYear,
        boolean checkoutEnabled,
        DisplayPrice price,
        List<TaxYear> years
) {

    /** Display-only list price; Stripe remains the source of truth for what is charged. */
    public record DisplayPrice(long amountCents, String currency) {
    }

    public record TaxYear(
            int taxYear,
            long closedTradeCount,
            TaxYearPassStatus passStatus,
            boolean unlocked,
            boolean purchasable
    ) {
    }
}
