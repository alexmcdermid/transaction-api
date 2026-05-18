package com.transactionapi.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.transactionapi.constants.ApiPaths;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void csrfEndpointReturnsToken() throws Exception {
        mockMvc.perform(get(ApiPaths.AUTH_CSRF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").isNotEmpty())
                .andExpect(jsonPath("$.parameterName").isNotEmpty())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void loginCreatesSessionCookieAndAuthenticatesSubsequentRequests() throws Exception {
        Jwt jwt = Jwt.withTokenValue("google-id-token")
                .header("alg", "RS256")
                .subject("google-sub-1")
                .claim("email", "user@example.com")
                .claim("name", "Example User")
                .build();
        when(jwtDecoder.decode("google-id-token")).thenReturn(jwt);

        MvcResult loginResult = loginWithCsrf("google-id-token")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authId").value("google-sub-1"))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(get(ApiPaths.USER_ME).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authId").value("google-sub-1"))
                .andExpect(jsonPath("$.email").value("user@example.com"));
    }

    @Test
    void loginRejectsInvalidCredential() throws Exception {
        when(jwtDecoder.decode("bad-token")).thenThrow(new JwtException("bad token"));

        loginWithCsrf("bad-token")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credential"));
    }

    @Test
    void loginRequiresCsrfToken() throws Exception {
        mockMvc.perform(
                        post(ApiPaths.AUTH_LOGIN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"credential\":\"google-id-token\"}")
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutInvalidatesSessionAndClearsCookies() throws Exception {
        Jwt jwt = Jwt.withTokenValue("google-id-token")
                .header("alg", "RS256")
                .subject("google-sub-1")
                .claim("email", "user@example.com")
                .claim("name", "Example User")
                .build();
        when(jwtDecoder.decode("google-id-token")).thenReturn(jwt);

        MvcResult loginResult = loginWithCsrf("google-id-token")
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        MvcResult logoutResult = mockMvc.perform(
                        post(ApiPaths.AUTH_LOGOUT)
                                .session(session)
                                .with(csrf())
                )
                .andExpect(status().isNoContent())
                .andReturn();

        Cookie sessionCookie = logoutResult.getResponse().getCookie("JSESSIONID");
        Cookie csrfCookie = logoutResult.getResponse().getCookie("XSRF-TOKEN");
        assertTrue(session.isInvalid());
        assertNotNull(sessionCookie);
        assertNotNull(csrfCookie);
        assertEquals(0, sessionCookie.getMaxAge());
        assertEquals(0, csrfCookie.getMaxAge());
    }

    private org.springframework.test.web.servlet.ResultActions loginWithCsrf(String credential) throws Exception {
        MvcResult csrfResult = mockMvc.perform(get(ApiPaths.AUTH_CSRF))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode csrf = objectMapper.readTree(csrfResult.getResponse().getContentAsString());
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        MockHttpSession csrfSession = (MockHttpSession) csrfResult.getRequest().getSession(false);

        MockHttpServletRequestBuilder request = post(ApiPaths.AUTH_LOGIN)
                .header(csrf.get("headerName").asText(), csrf.get("token").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credential\":\"" + credential + "\"}");
        if (csrfCookie != null) {
            request.cookie(csrfCookie);
        }
        if (csrfSession != null) {
            request.session(csrfSession);
        }

        return mockMvc.perform(request);
    }
}
