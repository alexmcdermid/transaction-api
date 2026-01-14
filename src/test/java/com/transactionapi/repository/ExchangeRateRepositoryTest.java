package com.transactionapi.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.transactionapi.constants.Currency;
import com.transactionapi.model.ExchangeRate;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class ExchangeRateRepositoryTest {

    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

    @Test
    void findsLatestRateByPair() {
        ExchangeRate older = new ExchangeRate();
        older.setBaseCurrency(Currency.CAD);
        older.setQuoteCurrency(Currency.USD);
        older.setEffectiveDate(LocalDate.of(2024, 1, 1));
        older.setRate(new BigDecimal("0.720000"));

        ExchangeRate newer = new ExchangeRate();
        newer.setBaseCurrency(Currency.CAD);
        newer.setQuoteCurrency(Currency.USD);
        newer.setEffectiveDate(LocalDate.of(2024, 1, 5));
        newer.setRate(new BigDecimal("0.730000"));

        exchangeRateRepository.save(older);
        exchangeRateRepository.save(newer);

        ExchangeRate latest = exchangeRateRepository
                .findTopByBaseCurrencyAndQuoteCurrencyOrderByEffectiveDateDesc(Currency.CAD, Currency.USD)
                .orElse(null);

        assertThat(latest).isNotNull();
        assertThat(latest.getEffectiveDate()).isEqualTo(LocalDate.of(2024, 1, 5));
        assertThat(latest.getRate()).isEqualByComparingTo("0.730000");
    }

    @Test
    void findsRateByPairAndDate() {
        ExchangeRate rate = new ExchangeRate();
        rate.setBaseCurrency(Currency.CAD);
        rate.setQuoteCurrency(Currency.USD);
        rate.setEffectiveDate(LocalDate.of(2024, 2, 1));
        rate.setRate(new BigDecimal("0.740000"));

        exchangeRateRepository.save(rate);

        ExchangeRate found = exchangeRateRepository
                .findByBaseCurrencyAndQuoteCurrencyAndEffectiveDate(
                        Currency.CAD,
                        Currency.USD,
                        LocalDate.of(2024, 2, 1)
                )
                .orElse(null);

        assertThat(found).isNotNull();
        assertThat(found.getRate()).isEqualByComparingTo("0.740000");
    }
}
