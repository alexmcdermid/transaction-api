package com.transactionapi.dto;

import java.time.LocalDate;
import java.util.List;

public record AnnualSummaryResponse(
        int taxYear,
        int tradeCount,
        LocalDate firstClosedOn,
        LocalDate lastClosedOn,
        int repurchaseFlagCount,
        List<SummaryRow> totals,
        List<SummaryRow> months,
        List<SummaryRow> accounts,
        List<SummaryRow> symbols,
        List<SummaryRow> assetTypes,
        List<SummaryRow> taxTreatment
) {
}
