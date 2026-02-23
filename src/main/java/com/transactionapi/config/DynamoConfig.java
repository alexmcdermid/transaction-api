package com.transactionapi.config;

import com.transactionapi.service.DynamoExchangeRateReader;
import com.transactionapi.service.DynamoJwtJwkSetReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
@ConditionalOnExpression(
        "'${app.fx.source:http}'.equalsIgnoreCase('dynamo') || " +
        "'${app.security.jwt.jwk-source:issuer}'.equalsIgnoreCase('dynamo')"
)
public class DynamoConfig {

    @Bean
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
    public DynamoJwtJwkSetReader dynamoJwtJwkSetReader(
            DynamoDbClient dynamo,
            @Value("${app.security.jwt.dynamo.table:AuthJwks}") String tableName,
            @Value("${app.security.jwt.dynamo.key-name:issuer}") String keyName,
            @Value("${app.security.jwt.issuer-uri:https://accounts.google.com}") String keyValue,
            @Value("${app.security.jwt.dynamo.jwk-set-attribute:jwkSet}") String jwkSetAttribute
    ) {
        return new DynamoJwtJwkSetReader(dynamo, tableName, keyName, keyValue, jwkSetAttribute);
    }
}
