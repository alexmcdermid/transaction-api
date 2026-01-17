package com.transactionapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.transactionapi.constants.ApiPaths;
import com.transactionapi.constants.PnlDisplayMode;
import com.transactionapi.constants.ThemeMode;
import com.transactionapi.dto.UserPreferencesRequest;
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
                .andExpect(jsonPath("$.pnlDisplayMode").value("PNL"));
    }

    @Test
    void updatesAndReturnsThemePreference() throws Exception {
        UserPreferencesRequest request = new UserPreferencesRequest(ThemeMode.DARK, PnlDisplayMode.PERCENT);

        mockMvc.perform(
                        put(ApiPaths.USER_PREFERENCES)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-User-Id", "pref-user-update")
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.themeMode").value("DARK"));

        mockMvc.perform(
                        get(ApiPaths.USER_PREFERENCES)
                                .header("X-User-Id", "pref-user-update")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.themeMode").value("DARK"))
                .andExpect(jsonPath("$.pnlDisplayMode").value("PERCENT"));
    }

    @Test
    void rejectsMissingThemeMode() throws Exception {
        mockMvc.perform(
                        put(ApiPaths.USER_PREFERENCES)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-User-Id", "pref-user-missing")
                                .content("{}")
                )
                .andExpect(status().isBadRequest());
    }
}
