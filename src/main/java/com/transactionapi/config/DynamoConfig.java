package com.transactionapi.config;

import com.transactionapi.service.DynamoExchangeRateReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
@ConditionalOnProperty(value = "app.fx.source", havingValue = "dynamo")
public class DynamoConfig {

    @Bean
    public DynamoDbClient dynamoDbClient(@Value("${app.aws.region}") String region) {
        return DynamoDbClient.builder()
                .region(Region.of(region))
                .build();
    }

    @Bean
    public DynamoExchangeRateReader dynamoExchangeRateReader(
            DynamoDbClient dynamo,
            @Value("${app.fx.dynamo.table:ExchangeRates}") String tableName
    ) {
        return new DynamoExchangeRateReader(dynamo, tableName);
    }
}
