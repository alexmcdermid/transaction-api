package com.transactionapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionControllerTest {

    private static final String USER_HEADER = "X-User-Id";
    private static final String USER = "test-user";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID accountId;

    @BeforeEach
    void setupAccount() throws Exception {
        accountId = createAccount("Investment");
    }

    @Test
    void createAndListTransactions() throws Exception {
        String payload = """
                {
                  "type": "DEPOSIT",
                  "amount": 1000.50,
                  "occurredAt": "2024-01-01T10:00:00Z",
                  "notes": "Initial funding"
                }
                """;

        MvcResult txResult = mockMvc.perform(post("/api/v1/accounts/{id}/transactions", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(USER_HEADER, USER)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode tx = objectMapper.readTree(txResult.getResponse().getContentAsString());
        assertThat(UUID.fromString(tx.get("accountId").asText())).isEqualTo(accountId);
        assertThat(tx.get("type").asText()).isEqualTo("DEPOSIT");
        assertThat(tx.get("amount").decimalValue()).isEqualByComparingTo("1000.50");

        MvcResult listResult = mockMvc.perform(get("/api/v1/accounts/{id}/transactions", accountId)
                        .header(USER_HEADER, USER))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode list = objectMapper.readTree(listResult.getResponse().getContentAsString());
        assertThat(list.isArray()).isTrue();
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("type").asText()).isEqualTo("DEPOSIT");
    }

    @Test
    void missingUserHeaderReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}/transactions", accountId))
                .andExpect(status().isUnauthorized());
    }

    private UUID createAccount(String name) throws Exception {
        String payload = """
                {
                  "name": "%s",
                  "type": "BANK",
                  "currency": "CAD"
                }
                """.formatted(name);

        MvcResult result = mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(USER_HEADER, USER)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(node.get("id").asText());
    }
}
