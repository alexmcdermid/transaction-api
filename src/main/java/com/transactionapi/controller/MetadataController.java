package com.transactionapi.controller;

import com.transactionapi.constants.AccountStatus;
import com.transactionapi.constants.AccountType;
import com.transactionapi.constants.ApiPaths;
import com.transactionapi.constants.Currency;
import com.transactionapi.constants.Exchange;
import com.transactionapi.constants.OptionType;
import com.transactionapi.constants.TransactionType;
import com.transactionapi.dto.EnumMetadataResponse;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.API_V1 + "/meta")
public class MetadataController {

    @GetMapping("/enums")
    public EnumMetadataResponse enums() {
        return new EnumMetadataResponse(
                enumNames(AccountType.values()),
                enumNames(AccountStatus.values()),
                enumNames(TransactionType.values()),
                enumNames(OptionType.values()),
                enumNames(Currency.values()),
                enumNames(Exchange.values())
        );
    }

    private List<String> enumNames(Enum<?>[] values) {
        return Arrays.stream(values)
                .map(Enum::name)
                .toList();
    }
}
