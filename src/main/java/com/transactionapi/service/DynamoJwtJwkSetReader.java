package com.transactionapi.service;

import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

public class DynamoJwtJwkSetReader {

    private final DynamoDbClient dynamo;
    private final String tableName;
    private final String keyName;
    private final String keyValue;
    private final String jwkSetAttribute;

    public DynamoJwtJwkSetReader(
            DynamoDbClient dynamo,
            String tableName,
            String keyName,
            String keyValue,
            String jwkSetAttribute
    ) {
        this.dynamo = dynamo;
        this.tableName = tableName;
        this.keyName = keyName;
        this.keyValue = keyValue;
        this.jwkSetAttribute = jwkSetAttribute;
    }

    public String getJwkSetJson() {
        Map<String, AttributeValue> key = Map.of(
                keyName, AttributeValue.builder().s(keyValue).build()
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

        AttributeValue jwkSetAttr = item.get(jwkSetAttribute);
        if (jwkSetAttr == null) {
            return null;
        }

        String jwkSetJson = jwkSetAttr.s();
        if (jwkSetJson == null || jwkSetJson.isBlank()) {
            return null;
        }

        return jwkSetJson;
    }
}
