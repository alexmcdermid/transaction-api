package com.transactionapi.service;

import com.transactionapi.constants.Currency;
import com.transactionapi.model.ExchangeRate;
import com.transactionapi.repository.ExchangeRateRepository;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Fetches CAD/USD once per startup and daily from the CBSA/BoC endpoint.
 * Converts CAD-per-USD to USD-per-CAD and caches the value with a fallback.
 */
@Service
public class ExchangeRateService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);
    private static final Currency BASE_CURRENCY = Currency.CAD;
    private static final Currency QUOTE_CURRENCY = Currency.USD;

    private final RestTemplate restTemplate;
    private final ExchangeRateRepository exchangeRateRepository;
    private final String endpoint;
    private final BigDecimal fallbackRate;
    private final ZoneId effectiveZone;
    private final AtomicReference<BigDecimal> cadToUsd = new AtomicReference<>();
    private final AtomicReference<LocalDate> lastUpdated = new AtomicReference<>();

    public ExchangeRateService(
            RestTemplateBuilder builder,
            ExchangeRateRepository exchangeRateRepository,
            @Value("${app.fx.cad-usd.url:https://bcd-api-dca-ipa.cbsa-asfc.cloud-nuage.canada.ca/exchange-rate-lambda/exchange-rates}") String endpoint,
            @Value("${app.fx.cad-usd.fallback:0.732}") BigDecimal fallbackRate,
            @Value("${app.fx.timeout-ms:2000}") int timeoutMs,
            @Value("${app.fx.effective-zone:America/Los_Angeles}") String effectiveZoneId
    ) {
        this.exchangeRateRepository = exchangeRateRepository;
        this.endpoint = endpoint;
        this.fallbackRate = fallbackRate.setScale(3, RoundingMode.HALF_UP);
        this.effectiveZone = parseZone(effectiveZoneId);
        this.restTemplate = builder
                .requestFactory(() -> {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout(timeoutMs);
                    factory.setReadTimeout(timeoutMs);
                    return factory;
                })
                .build();
        this.cadToUsd.set(this.fallbackRate);
        this.lastUpdated.set(LocalDate.now(effectiveZone));
    }

    public BigDecimal cadToUsd() {
        return cadToUsd.get();
    }

    public LocalDate lastUpdatedOn() {
        LocalDate date = lastUpdated.get();
        return date != null ? date : LocalDate.now(effectiveZone);
    }

    @PostConstruct
    public void init() {
        log.info("Initializing CAD/USD rate with fallback {}", cadToUsd.get());
        loadLatestFromDatabase();
        refreshDaily();
    }

    @Scheduled(cron = "0 30 2 * * *")
    public void refreshDaily() {
        try {
            log.info("Refreshing CAD/USD rate from {}", endpoint);
            Map<?, ?> response = restTemplate.getForObject(endpoint, Map.class);
            RateQuote quote = extractUsdRate(response);
            if (quote != null) {
                cadToUsd.set(quote.rate());
                lastUpdated.set(quote.date());
                persistRateQuote(quote);
                log.info("CAD/USD rate updated to {} on {}", quote.rate(), quote.date());
            } else {
                log.warn("CAD/USD response missing usable rate, keeping cached {}", cadToUsd.get());
            }
        } catch (Exception ex) {
            log.warn("Unable to refresh CAD/USD rate, keeping cached value {}", cadToUsd.get(), ex);
        }
    }

    private void loadLatestFromDatabase() {
        try {
            exchangeRateRepository
                    .findTopByBaseCurrencyAndQuoteCurrencyOrderByEffectiveDateDesc(BASE_CURRENCY, QUOTE_CURRENCY)
                    .ifPresent(rate -> {
                        cadToUsd.set(rate.getRate());
                        lastUpdated.set(rate.getEffectiveDate());
                        log.info("Loaded CAD/USD rate {} on {} from history", rate.getRate(), rate.getEffectiveDate());
                    });
        } catch (Exception ex) {
            log.warn("Unable to load CAD/USD rate history, using fallback {}", cadToUsd.get(), ex);
        }
    }

    private void persistRateQuote(RateQuote quote) {
        try {
            ExchangeRate rate = exchangeRateRepository
                    .findByBaseCurrencyAndQuoteCurrencyAndEffectiveDate(BASE_CURRENCY, QUOTE_CURRENCY, quote.date())
                    .map(existing -> {
                        existing.setRate(quote.rate());
                        return existing;
                    })
                    .orElseGet(() -> {
                        ExchangeRate created = new ExchangeRate();
                        created.setBaseCurrency(BASE_CURRENCY);
                        created.setQuoteCurrency(QUOTE_CURRENCY);
                        created.setEffectiveDate(quote.date());
                        created.setRate(quote.rate());
                        return created;
                    });
            exchangeRateRepository.save(rate);
        } catch (Exception ex) {
            log.warn("Unable to persist CAD/USD rate history for {}", quote.date(), ex);
        }
    }

    private RateQuote extractUsdRate(Map<?, ?> response) {
        if (response == null) {
            return null;
        }

        Object fx = response.get("ForeignExchangeRates");
        if (fx instanceof List<?> list) {
            return extractUsdRateFromList(list);
        }

        return null;
    }

    private RateQuote extractUsdRateFromList(List<?> list) {
        return list.stream()
                .filter(item -> item instanceof Map<?, ?>)
                .map(item -> (Map<?, ?>) item)
                .filter(this::isUsdToCad)
                .sorted(Comparator.comparing(this::effectiveTimestamp).reversed())
                .map(this::toRateQuote)
                .filter(quote -> quote != null && quote.rate().compareTo(BigDecimal.ZERO) > 0)
                .findFirst()
                .orElse(null);
    }

    private boolean isUsdToCad(Map<?, ?> entry) {
        Object from = extractCurrency(entry.get("FromCurrency"));
        Object to = extractCurrency(entry.get("ToCurrency"));
        return "USD".equalsIgnoreCase(String.valueOf(from)) && "CAD".equalsIgnoreCase(String.valueOf(to));
    }

    private Object extractCurrency(Object node) {
        if (node instanceof Map<?, ?> map) {
            Object val = map.get("Value");
            if (val != null) {
                return val;
            }
        }
        return node;
    }

    private ZonedDateTime effectiveTimestamp(Map<?, ?> entry) {
        Object ts = entry.get("ExchangeRateEffectiveTimestamp");
        if (ts instanceof String s) {
            try {
                return ZonedDateTime.parse(s);
            } catch (Exception ignored) {
            }
        }
        return ZonedDateTime.now(effectiveZone);
    }

    private RateQuote toRateQuote(Map<?, ?> entry) {
        Object rateObj = entry.get("Rate");
        BigDecimal cadPerUsd = null;
        if (rateObj instanceof Number number) {
            cadPerUsd = BigDecimal.valueOf(number.doubleValue());
        } else if (rateObj instanceof String s) {
            try {
                cadPerUsd = new BigDecimal(s);
            } catch (NumberFormatException ignored) {
            }
        }
        if (cadPerUsd != null && cadPerUsd.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal usdPerCad = BigDecimal.ONE.divide(cadPerUsd, 6, RoundingMode.HALF_UP);
            LocalDate effectiveDate = effectiveTimestamp(entry)
                    .withZoneSameInstant(effectiveZone)
                    .toLocalDate();
            return new RateQuote(usdPerCad, effectiveDate);
        }
        return null;
    }

    private record RateQuote(BigDecimal rate, LocalDate date) {
    }

    private ZoneId parseZone(String zoneId) {
        try {
            return ZoneId.of(zoneId);
        } catch (Exception ex) {
            log.warn("Invalid fx effective zone {}, falling back to system default", zoneId, ex);
            return ZoneId.systemDefault();
        }
    }
}
