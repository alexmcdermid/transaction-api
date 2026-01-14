package com.transactionapi.repository;

import com.transactionapi.constants.Currency;
import com.transactionapi.model.ExchangeRate;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, UUID> {

    Optional<ExchangeRate> findTopByBaseCurrencyAndQuoteCurrencyOrderByEffectiveDateDesc(
            Currency baseCurrency,
            Currency quoteCurrency
    );

    Optional<ExchangeRate> findByBaseCurrencyAndQuoteCurrencyAndEffectiveDate(
            Currency baseCurrency,
            Currency quoteCurrency,
            LocalDate effectiveDate
    );
}
