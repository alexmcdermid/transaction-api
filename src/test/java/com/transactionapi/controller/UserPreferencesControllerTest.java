package com.transactionapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.transactionapi.constants.ApiPaths;
import com.transactionapi.constants.DashboardWidget;
import com.transactionapi.constants.PnlDisplayMode;
import com.transactionapi.constants.ThemeMode;
import com.transactionapi.constants.TradeSortDirection;
import com.transactionapi.constants.TradeSortField;
import com.transactionapi.dto.UserPreferencesRequest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserPreferencesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void returnsDefaultThemeForNewUser() throws Exception {
        mockMvc.perform(
                        get(ApiPaths.USER_PREFERENCES)
                                .header("X-User-Id", "pref-user-default")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.themeMode").value("LIGHT"))
                .andExpect(jsonPath("$.pnlDisplayMode").value("PNL"))
                .andExpect(jsonPath("$.defaultTradeSortBy").value("CLOSED_AT"))
                .andExpect(jsonPath("$.defaultTradeSortDirection").value("DESC"))
                .andExpect(jsonPath("$.showTradeHistory").value(false))
                .andExpect(jsonPath("$.dashboardWidgets[0]").value("TOTAL_REALIZED"))
                .andExpect(jsonPath("$.dashboardWidgets[1]").value("BEST_MONTH"))
                .andExpect(jsonPath("$.dashboardWidgets[2]").value("BEST_DAY"))
                .andExpect(jsonPath("$.taxCapitalGainsRate").value(50.00))
                .andExpect(jsonPath("$.taxPersonalRate").value(50.00));
    }

    @Test
    void updatesAndReturnsThemePreference() throws Exception {
        UserPreferencesRequest request = new UserPreferencesRequest(
                ThemeMode.DARK,
                PnlDisplayMode.PERCENT,
                TradeSortField.SYMBOL,
                TradeSortDirection.ASC,
                true,
                List.of(DashboardWidget.TOTAL_REALIZED, DashboardWidget.TAX_OWED),
                new BigDecimal("50.00"),
                new BigDecimal("38.50")
        );

        mockMvc.perform(
                        put(ApiPaths.USER_PREFERENCES)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-User-Id", "pref-user-update")
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.themeMode").value("DARK"))
                .andExpect(jsonPath("$.defaultTradeSortBy").value("SYMBOL"))
                .andExpect(jsonPath("$.defaultTradeSortDirection").value("ASC"))
                .andExpect(jsonPath("$.showTradeHistory").value(true))
                .andExpect(jsonPath("$.dashboardWidgets[0]").value("TOTAL_REALIZED"))
                .andExpect(jsonPath("$.dashboardWidgets[1]").value("TAX_OWED"))
                .andExpect(jsonPath("$.taxCapitalGainsRate").value(50.00))
                .andExpect(jsonPath("$.taxPersonalRate").value(38.50));

        mockMvc.perform(
                        get(ApiPaths.USER_PREFERENCES)
                                .header("X-User-Id", "pref-user-update")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.themeMode").value("DARK"))
                .andExpect(jsonPath("$.pnlDisplayMode").value("PERCENT"))
                .andExpect(jsonPath("$.defaultTradeSortBy").value("SYMBOL"))
                .andExpect(jsonPath("$.defaultTradeSortDirection").value("ASC"))
                .andExpect(jsonPath("$.showTradeHistory").value(true))
                .andExpect(jsonPath("$.dashboardWidgets[0]").value("TOTAL_REALIZED"))
                .andExpect(jsonPath("$.dashboardWidgets[1]").value("TAX_OWED"))
                .andExpect(jsonPath("$.taxCapitalGainsRate").value(50.00))
                .andExpect(jsonPath("$.taxPersonalRate").value(38.50));
    }

    @Test
    void rejectsMissingPreferencesPayload() throws Exception {
        mockMvc.perform(
                        put(ApiPaths.USER_PREFERENCES)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-User-Id", "pref-user-missing")
                                .content("{}")
                )
                .andExpect(status().isBadRequest());
    }
}
