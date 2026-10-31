package com.transactionapi.dto;

import com.transactionapi.constants.TaxYearReportType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * {@code includeNotes} only affects the trade ledger and is false when omitted. It is a Boolean because
 * Jackson 3 rejects a missing primitive (FAIL_ON_NULL_FOR_PRIMITIVES is on by default).
 */
public record TaxYearExportRequest(
        @NotNull TaxYearReportType reportType,
        @Valid ReportFilters filters,
        Boolean includeNotes
) {
}
