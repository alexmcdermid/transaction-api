package com.transactionapi.dto;

import com.transactionapi.constants.AssetType;
import com.transactionapi.constants.Currency;
import com.transactionapi.constants.TradeDirection;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Optional tax year report filters. Null or empty lists mean no filter on that field, and
 * {@code includeUnassigned} defaults to true when omitted.
 */
public record ReportFilters(
        @Size(max = 100) List<UUID> accountIds,
        Boolean includeUnassigned,
        @Size(max = 100) List<@Size(max = 12) String> symbols,
        @Size(max = 10) List<AssetType> assetTypes,
        @Size(max = 10) List<Currency> currencies,
        @Size(max = 10) List<TradeDirection> directions
) {
}
