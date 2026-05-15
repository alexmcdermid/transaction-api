package com.transactionapi.security;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

public class DynamoJwkSetReader {

    private final DynamoDbClient dynamo;
    private final String tableName;
    private final String keyAttribute;
    private final String keyValue;
    private final String jwkSetAttribute;
    private final String expiresAtAttribute;
    private final Duration maxStale;

    public DynamoJwkSetReader(
            DynamoDbClient dynamo,
            String tableName,
            String keyAttribute,
            String keyValue,
            String jwkSetAttribute,
            String expiresAtAttribute,
            Duration maxStale
    ) {
        this.dynamo = dynamo;
        this.tableName = tableName;
        this.keyAttribute = keyAttribute;
        this.keyValue = keyValue;
        this.jwkSetAttribute = jwkSetAttribute;
        this.expiresAtAttribute = expiresAtAttribute;
        this.maxStale = maxStale;
    }

    public String getJwkSet() {
        Map<String, AttributeValue> key = Map.of(
                keyAttribute, AttributeValue.builder().s(keyValue).build()
        );

        GetItemRequest req = GetItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .consistentRead(true)
                .build();

        Map<String, AttributeValue> item = dynamo.getItem(req).item();
        if (item == null || item.isEmpty()) {
            throw new IllegalStateException("JWKS item not found in DynamoDB");
        }

        String jwks = stringValue(item.get(jwkSetAttribute));
        if (!StringUtils.hasText(jwks)) {
            throw new IllegalStateException("JWKS item is missing " + jwkSetAttribute);
        }

        Instant expiresAt = instantValue(item.get(expiresAtAttribute));
        if (expiresAt != null && maxStale != null && Instant.now().isAfter(expiresAt.plus(maxStale))) {
            throw new IllegalStateException("JWKS item is stale");
        }

        return jwks;
    }

    private String stringValue(AttributeValue attr) {
        if (attr == null) {
            return null;
        }
        return attr.s();
    }

    private Instant instantValue(AttributeValue attr) {
        if (attr == null) {
            return null;
        }
        String value = attr.s();
        if (!StringUtils.hasText(value)) {
            value = attr.n();
        }
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            try {
                return Instant.ofEpochSecond(Long.parseLong(value));
            } catch (NumberFormatException ex) {
                throw new IllegalStateException("Invalid JWKS expiresAt value");
            }
        }
    }
}
