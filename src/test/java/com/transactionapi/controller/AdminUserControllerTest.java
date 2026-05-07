package com.transactionapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.transactionapi.constants.ApiPaths;
import com.transactionapi.constants.AssetType;
import com.transactionapi.constants.Currency;
import com.transactionapi.constants.TradeDirection;
import com.transactionapi.dto.TradeRequest;
import com.transactionapi.model.User;
import com.transactionapi.repository.UserRepository;
import com.transactionapi.service.TradeService;
import com.transactionapi.service.UserService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.security.admin-emails=admin@example.com")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private TradeService tradeService;

    @Test
    void adminCanViewTradeHistoryForUser() throws Exception {
        String targetAuthId = "admin-history-user";
        userService.ensureUserExists(targetAuthId, "target@example.com");

        TradeRequest create = new TradeRequest(
                "TSLA",
                AssetType.STOCK,
                Currency.USD,
                TradeDirection.LONG,
                2,
                new BigDecimal("100.00"),
                new BigDecimal("105.00"),
                BigDecimal.ZERO,
                null,
                null,
                null,
                LocalDate.of(2024, 5, 1),
                LocalDate.of(2024, 5, 1),
                null
        );
        TradeRequest edit = new TradeRequest(
                "NVDA",
                AssetType.STOCK,
                Currency.USD,
                TradeDirection.LONG,
                1,
                new BigDecimal("200.00"),
                new BigDecimal("210.00"),
                BigDecimal.ZERO,
                null,
                null,
                null,
                LocalDate.of(2024, 5, 2),
                LocalDate.of(2024, 5, 2),
                "admin-visible"
        );

        var created = tradeService.createTrade(create, targetAuthId);
        tradeService.updateTrade(created.id(), edit, targetAuthId);

        User target = userRepository.findByAuthId(targetAuthId).orElseThrow();

        mockMvc.perform(
                        get(ApiPaths.ADMIN_USERS + "/" + target.getId() + "/trade-history")
                                .header("X-User-Id", "admin@example.com")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].action").value("EDIT"))
                .andExpect(jsonPath("$[0].symbol").value("NVDA"))
                .andExpect(jsonPath("$[1].action").value("CREATE"))
                .andExpect(jsonPath("$[1].symbol").value("TSLA"));
    }

    @Test
    void nonAdminCannotViewTradeHistoryForUser() throws Exception {
        mockMvc.perform(
                        get(ApiPaths.ADMIN_USERS + "/" + java.util.UUID.randomUUID() + "/trade-history")
                                .header("X-User-Id", "not-admin@example.com")
                )
                .andExpect(status().isForbidden());
    }
}
