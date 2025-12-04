package com.transactionapi.dto;

import java.util.List;

public record EnumMetadataResponse(
        List<String> accountTypes,
        List<String> accountStatuses,
        List<String> transactionTypes,
        List<String> optionTypes,
        List<String> currencies,
        List<String> exchanges
) {
}
