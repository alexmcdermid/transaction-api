package com.transactionapi.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

class DynamoJwkSetReaderTest {

    @Test
    void readsJwksFromConfiguredItem() {
        DynamoDbClient dynamo = mock(DynamoDbClient.class);
        String jwks = "{\"keys\":[]}";
        when(dynamo.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder()
                .item(Map.of(
                        "provider", AttributeValue.builder().s("google").build(),
                        "jwks", AttributeValue.builder().s(jwks).build(),
                        "expiresAt", AttributeValue.builder().s(Instant.now().plus(Duration.ofHours(1)).toString()).build()
                ))
                .build());

        DynamoJwkSetReader reader = new DynamoJwkSetReader(
                dynamo,
                "AuthJwks",
                "provider",
                "google",
                "jwks",
                "expiresAt",
                Duration.ofHours(24)
        );

        assertThat(reader.getJwkSet()).isEqualTo(jwks);
    }

    @Test
    void rejectsStaleJwks() {
        DynamoDbClient dynamo = mock(DynamoDbClient.class);
        when(dynamo.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder()
                .item(Map.of(
                        "provider", AttributeValue.builder().s("google").build(),
                        "jwks", AttributeValue.builder().s("{\"keys\":[]}").build(),
                        "expiresAt", AttributeValue.builder().s(Instant.now().minus(Duration.ofDays(5)).toString()).build()
                ))
                .build());

        DynamoJwkSetReader reader = new DynamoJwkSetReader(
                dynamo,
                "AuthJwks",
                "provider",
                "google",
                "jwks",
                "expiresAt",
                Duration.ofHours(24)
        );

        assertThatThrownBy(reader::getJwkSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stale");
    }
}
