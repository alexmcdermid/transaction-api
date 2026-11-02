package com.transactionapi.dto;

import com.transactionapi.constants.TaxYearPassStatus;
import com.transactionapi.model.TaxYearPass;
import java.time.Instant;
import java.util.UUID;

public record TaxYearPassResponse(
        UUID id,
        int taxYear,
        TaxYearPassStatus status,
        Long amountTotal,
        String currency,
        Instant paidAt,
        Instant refundedAt,
        Instant createdAt
) {
    public static TaxYearPassResponse from(TaxYearPass pass) {
        return new TaxYearPassResponse(
                pass.getId(),
                pass.getTaxYear(),
                pass.getStatus(),
                pass.getAmountTotal(),
                pass.getCurrency(),
                pass.getPaidAt(),
                pass.getRefundedAt(),
                pass.getCreatedAt()
        );
    }
}
