package com.transactionapi.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

public class DynamoExchangeRateReader {

    private final DynamoDbClient dynamo;
    private final String tableName;

    public DynamoExchangeRateReader(DynamoDbClient dynamo, String tableName) {
        this.dynamo = dynamo;
        this.tableName = tableName;
    }

    public RateQuote getCadUsdLatest() {
        Map<String, AttributeValue> key = Map.of(
                "pair", AttributeValue.builder().s("CADUSD").build()
        );

        GetItemRequest req = GetItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .consistentRead(true)
                .build();

        Map<String, AttributeValue> item = dynamo.getItem(req).item();
        if (item == null || item.isEmpty()) {
            return null;
        }

        AttributeValue rateAttr = item.get("rate");
        AttributeValue dateAttr = item.get("effectiveDate");
        String rateStr = rateAttr != null ? rateAttr.s() : null;
        String dateStr = dateAttr != null ? dateAttr.s() : null;
        if (rateStr == null || dateStr == null) {
            return null;
        }

        BigDecimal rate;
        try {
            rate = new BigDecimal(rateStr);
        } catch (Exception ex) {
            return null;
        }

        LocalDate date;
        try {
            date = LocalDate.parse(dateStr);
        } catch (Exception ex) {
            return null;
        }

        if (rate.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        return new RateQuote(rate, date);
    }

    public record RateQuote(BigDecimal rate, LocalDate date) {
    }
}
