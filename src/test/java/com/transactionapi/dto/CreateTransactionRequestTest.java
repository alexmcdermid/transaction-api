package com.transactionapi.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.transactionapi.constants.Currency;
import com.transactionapi.constants.Exchange;
import com.transactionapi.constants.OptionType;
import com.transactionapi.constants.TransactionType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CreateTransactionRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setup() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void optionTypeRequiresStrikeAndExpiry() {
        CreateTransactionRequest invalid = new CreateTransactionRequest(
                TransactionType.BUY,
                new BigDecimal("10"),
                "AAPL",
                "Apple",
                Currency.USD,
                Exchange.NASDAQ,
                1,
                new BigDecimal("10"),
                OptionType.CALL,
                null, // missing strike
                null, // missing expiry
                null,
                null,
                null,
                Instant.now(),
                null
        );

        Set<ConstraintViolation<CreateTransactionRequest>> violations = validator.validate(invalid);
        assertThat(violations).anyMatch(v -> v.getMessage().contains("strikePrice and expiryDate are required"));
    }

    @Test
    void optionTypeWithStrikeAndExpiryPasses() {
        CreateTransactionRequest valid = new CreateTransactionRequest(
                TransactionType.BUY,
                new BigDecimal("10"),
                "AAPL",
                "Apple",
                Currency.USD,
                Exchange.NASDAQ,
                1,
                new BigDecimal("10"),
                OptionType.CALL,
                new BigDecimal("100"),
                java.time.LocalDate.now().plusDays(30),
                null,
                null,
                null,
                Instant.now(),
                null
        );

        Set<ConstraintViolation<CreateTransactionRequest>> violations = validator.validate(valid);
        assertThat(violations).isEmpty();
    }
}
