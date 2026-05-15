package com.transactionapi.config;

import com.transactionapi.service.DynamoExchangeRateReader;
import com.transactionapi.security.DynamoJwkSetReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
public class DynamoConfig {

    @Bean
    @ConditionalOnExpression("'${app.fx.source:http}' == 'dynamo' || '${app.security.jwt.jwk-source:issuer}' == 'dynamo'")
    public DynamoDbClient dynamoDbClient(@Value("${app.aws.region}") String region) {
        return DynamoDbClient.builder()
                .region(Region.of(region))
                .build();
    }

    @Bean
    @ConditionalOnProperty(value = "app.fx.source", havingValue = "dynamo")
    public DynamoExchangeRateReader dynamoExchangeRateReader(
            DynamoDbClient dynamo,
            @Value("${app.fx.dynamo.table:ExchangeRates}") String tableName
    ) {
        return new DynamoExchangeRateReader(dynamo, tableName);
    }

    @Bean
    @ConditionalOnProperty(value = "app.security.jwt.jwk-source", havingValue = "dynamo")
    public DynamoJwkSetReader dynamoJwkSetReader(
            DynamoDbClient dynamo,
            @Value("${app.security.jwt.dynamo.table:AuthJwks}") String tableName,
            @Value("${app.security.jwt.dynamo.key-attribute:provider}") String keyAttribute,
            @Value("${app.security.jwt.dynamo.key:google}") String keyValue,
            @Value("${app.security.jwt.dynamo.jwk-set-attribute:jwks}") String jwkSetAttribute,
            @Value("${app.security.jwt.dynamo.expires-at-attribute:expiresAt}") String expiresAtAttribute,
            @Value("${app.security.jwt.dynamo.max-stale:PT72H}") java.time.Duration maxStale
    ) {
        return new DynamoJwkSetReader(
                dynamo,
                tableName,
                keyAttribute,
                keyValue,
                jwkSetAttribute,
                expiresAtAttribute,
                maxStale
        );
    }
}
