package com.transactionapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
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
class AccountControllerTest {

    private static final String USER_HEADER = "X-User-Id";
    private static final String USER = "test-user";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createAndFetchAccount() throws Exception {
        UUID id = createAccount("Checking");

        MvcResult listResult = mockMvc.perform(get("/api/v1/accounts")
                        .header(USER_HEADER, USER))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode list = objectMapper.readTree(listResult.getResponse().getContentAsString());
        assertThat(list.isArray()).isTrue();
        assertThat(list).anyMatch(node -> UUID.fromString(node.get("id").asText()).equals(id));

        MvcResult getResult = mockMvc.perform(get("/api/v1/accounts/{id}", id)
                        .header(USER_HEADER, USER))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode account = objectMapper.readTree(getResult.getResponse().getContentAsString());
        assertThat(UUID.fromString(account.get("id").asText())).isEqualTo(id);
        assertThat(account.get("name").asText()).isEqualTo("Checking");
    }

    @Test
    void missingUserHeaderReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
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
