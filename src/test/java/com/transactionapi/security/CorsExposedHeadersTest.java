package com.transactionapi.security;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.transactionapi.constants.ApiPaths;
import com.transactionapi.constants.TaxYearPassStatus;
import com.transactionapi.model.TaxYearPass;
import com.transactionapi.model.User;
import com.transactionapi.repository.TaxYearPassRepository;
import com.transactionapi.service.UserService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.cors.allowed-origins=" + CorsExposedHeadersTest.FRONTEND_ORIGIN)
class CorsExposedHeadersTest {

    static final String FRONTEND_ORIGIN = "https://app.tradelog.test";
    private static final int TAX_YEAR = 2025;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private TaxYearPassRepository taxYearPassRepository;

    @Test
    void crossOriginExportLetsTheFrontendReadTheFilename() throws Exception {
        String authId = "cors-export-user-" + UUID.randomUUID();
        grantPaidPass(authId);

        mockMvc.perform(post(ApiPaths.REPORTS_TAX_YEARS + "/" + TAX_YEAR + "/exports")
                        .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
                        .header("X-User-Id", authId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportType\":\"TRADE_LEDGER\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        containsString(HttpHeaders.CONTENT_DISPOSITION)
                ))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"tradelog-" + TAX_YEAR + "-trade-ledger.csv\""
                ));
    }

    private void grantPaidPass(String authId) {
        User user = userService.getOrCreateUser(authId, null);
        TaxYearPass pass = new TaxYearPass();
        pass.setUserId(user.getId());
        pass.setTaxYear(TAX_YEAR);
        pass.setStatus(TaxYearPassStatus.PAID);
        taxYearPassRepository.save(pass);
    }
}
