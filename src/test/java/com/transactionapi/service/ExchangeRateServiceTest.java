package com.transactionapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transactionapi.constants.Currency;
import com.transactionapi.model.ExchangeRate;
import com.transactionapi.repository.ExchangeRateRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    private static final String ENDPOINT = "https://example.com/fx";

    @Mock
    private RestTemplateBuilder restTemplateBuilder;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @Mock
    private ObjectProvider<DynamoExchangeRateReader> dynamoReaderProvider;

    private ExchangeRateService exchangeRateService;

    @BeforeEach
    void setUp() {
        when(restTemplateBuilder.requestFactory(
                org.mockito.ArgumentMatchers.<Supplier<ClientHttpRequestFactory>>any()
        )).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.build()).thenReturn(restTemplate);
        when(dynamoReaderProvider.getIfAvailable()).thenReturn(null);
        exchangeRateService = new ExchangeRateService(
                restTemplateBuilder,
                exchangeRateRepository,
                dynamoReaderProvider,
                "http",
                ENDPOINT,
                new BigDecimal("0.732"),
                2000,
                "America/Los_Angeles"
        );
    }

    @Test
    void refreshDailyPersistsRateAndUpdatesCache() {
        Map<String, Object> entry = Map.of(
                "FromCurrency", Map.of("Value", "USD"),
                "ToCurrency", Map.of("Value", "CAD"),
                "Rate", "1.25",
                "ExchangeRateEffectiveTimestamp", "2024-12-05T08:00:00Z"
        );
        Map<String, Object> response = Map.of("ForeignExchangeRates", List.of(entry));
        when(restTemplate.getForObject(eq(ENDPOINT), eq(Map.class))).thenReturn(response);
        when(exchangeRateRepository.findByBaseCurrencyAndQuoteCurrencyAndEffectiveDate(
                Currency.CAD,
                Currency.USD,
                LocalDate.of(2024, 12, 5)
        )).thenReturn(Optional.empty());

        exchangeRateService.refreshDaily();

        ArgumentCaptor<ExchangeRate> captor = ArgumentCaptor.forClass(ExchangeRate.class);
        verify(exchangeRateRepository).save(captor.capture());
        ExchangeRate saved = captor.getValue();

        assertThat(saved.getBaseCurrency()).isEqualTo(Currency.CAD);
        assertThat(saved.getQuoteCurrency()).isEqualTo(Currency.USD);
        assertThat(saved.getEffectiveDate()).isEqualTo(LocalDate.of(2024, 12, 5));
        assertThat(saved.getRate()).isEqualByComparingTo("0.800000");
        assertThat(exchangeRateService.cadToUsd()).isEqualByComparingTo("0.800000");
        assertThat(exchangeRateService.lastUpdatedOn()).isEqualTo(LocalDate.of(2024, 12, 5));
    }

    @Test
    void refreshDailySkipsPersistWhenNoRate() {
        Map<String, Object> response = Map.of("ForeignExchangeRates", List.of());
        when(restTemplate.getForObject(eq(ENDPOINT), eq(Map.class))).thenReturn(response);

        BigDecimal cached = exchangeRateService.cadToUsd();
        LocalDate cachedDate = exchangeRateService.lastUpdatedOn();

        exchangeRateService.refreshDaily();

        verify(exchangeRateRepository, never()).save(any(ExchangeRate.class));
        assertThat(exchangeRateService.cadToUsd()).isEqualByComparingTo(cached);
        assertThat(exchangeRateService.lastUpdatedOn()).isEqualTo(cachedDate);
    }

    @Test
    void refreshDailyKeepsCachedOnError() {
        when(restTemplate.getForObject(eq(ENDPOINT), eq(Map.class)))
                .thenThrow(new RuntimeException("timeout"));

        BigDecimal cached = exchangeRateService.cadToUsd();
        LocalDate cachedDate = exchangeRateService.lastUpdatedOn();

        exchangeRateService.refreshDaily();

        verify(exchangeRateRepository, never()).save(any(ExchangeRate.class));
        assertThat(exchangeRateService.cadToUsd()).isEqualByComparingTo(cached);
        assertThat(exchangeRateService.lastUpdatedOn()).isEqualTo(cachedDate);
    }

    @Test
    void initLoadsLatestRateWhenRefreshFails() {
        ExchangeRate stored = new ExchangeRate();
        stored.setBaseCurrency(Currency.CAD);
        stored.setQuoteCurrency(Currency.USD);
        stored.setEffectiveDate(LocalDate.of(2024, 1, 2));
        stored.setRate(new BigDecimal("0.745000"));

        when(exchangeRateRepository.findTopByBaseCurrencyAndQuoteCurrencyOrderByEffectiveDateDesc(
                Currency.CAD,
                Currency.USD
        )).thenReturn(Optional.of(stored));
        when(restTemplate.getForObject(eq(ENDPOINT), eq(Map.class)))
                .thenThrow(new RuntimeException("Down"));

        exchangeRateService.init();

        assertThat(exchangeRateService.cadToUsd()).isEqualByComparingTo("0.745000");
        assertThat(exchangeRateService.lastUpdatedOn()).isEqualTo(LocalDate.of(2024, 1, 2));
    }
}
