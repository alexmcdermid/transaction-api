package com.transactionapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.transactionapi.constants.ApiPaths;
import com.transactionapi.constants.ShareType;
import com.transactionapi.dto.CreateShareLinkRequest;
import com.transactionapi.model.ShareLink;
import com.transactionapi.security.UserIdResolver;
import com.transactionapi.service.ShareLinkService;
import com.transactionapi.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShareLinkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ShareLinkService shareLinkService;

    @MockBean
    private UserIdResolver userIdResolver;

    @MockBean
    private UserService userService;

    private ShareLink publicShare;
    private ShareLink authRequiredShare;
    private static final String USER_ID = "test-user";

    @BeforeEach
    void setUp() {
        publicShare = new ShareLink();
        publicShare.setCode("abc12345");
        publicShare.setUserId(USER_ID);
        publicShare.setShareType(ShareType.SUMMARY);
        publicShare.setData("{\"test\":\"data\"}");
        publicShare.setRequiresAuth(false);
        publicShare.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        publicShare.setAccessCount(0);

        authRequiredShare = new ShareLink();
        authRequiredShare.setCode("xyz98765");
        authRequiredShare.setUserId(USER_ID);
        authRequiredShare.setShareType(ShareType.TRADES);
        authRequiredShare.setData("{\"test\":\"auth\"}");
        authRequiredShare.setRequiresAuth(true);
        authRequiredShare.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        authRequiredShare.setAccessCount(0);

        when(userIdResolver.requireUserId(any())).thenReturn(USER_ID);
        when(userIdResolver.resolveEmail(any())).thenReturn("test@example.com");
        doNothing().when(userService).ensureUserExists(anyString(), anyString());
    }

    @Test
    @WithMockUser
    void createShareLink_authenticated_shouldSucceed() throws Exception {
        CreateShareLinkRequest request = new CreateShareLinkRequest(
                ShareType.SUMMARY,
                "{\"test\":\"data\"}",
                false,
                7L
        );

        when(shareLinkService.createShareLink(
                eq(USER_ID),
                eq(ShareType.SUMMARY),
                eq("{\"test\":\"data\"}"),
                eq(false),
                eq(7L)
        )).thenReturn(publicShare);

        mockMvc.perform(post(ApiPaths.SHARES)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("abc12345"))
                .andExpect(jsonPath("$.shareType").value("SUMMARY"))
                .andExpect(jsonPath("$.requiresAuth").value(false));

        verify(userService).ensureUserExists(USER_ID, "test@example.com");
        verify(shareLinkService).createShareLink(USER_ID, ShareType.SUMMARY, "{\"test\":\"data\"}", false, 7L);
    }

    @Test
    void createShareLink_unauthenticated_shouldFail() throws Exception {
        CreateShareLinkRequest request = new CreateShareLinkRequest(
                ShareType.SUMMARY,
                "{\"test\":\"data\"}",
                false,
                7L
        );

        mockMvc.perform(post(ApiPaths.SHARES)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verify(shareLinkService, never()).createShareLink(anyString(), any(), anyString(), anyBoolean(), anyLong());
    }

    @Test
    void getShareLink_publicShare_unauthenticated_shouldSucceed() throws Exception {
        when(userIdResolver.requireUserId(any())).thenThrow(new RuntimeException("Unauthorized"));
        when(shareLinkService.findByCodeRaw("abc12345")).thenReturn(publicShare);
        when(shareLinkService.getShareLink(eq("abc12345"), isNull()))
                .thenReturn(Optional.of(publicShare));

        mockMvc.perform(get(ApiPaths.SHARES + "/abc12345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("abc12345"))
                .andExpect(jsonPath("$.data").value("{\"test\":\"data\"}"))
                .andExpect(jsonPath("$.requiresAuth").value(false));
    }

    @Test
    void getShareLink_authRequired_unauthenticated_shouldReturnForbidden() throws Exception {
        when(userIdResolver.requireUserId(any())).thenThrow(new RuntimeException("Unauthorized"));
        when(shareLinkService.findByCodeRaw("xyz98765")).thenReturn(authRequiredShare);

        mockMvc.perform(get(ApiPaths.SHARES + "/xyz98765"))
                .andExpect(status().isForbidden());

        verify(shareLinkService, never()).getShareLink(anyString(), anyString());
    }

    @Test
    @WithMockUser
    void getShareLink_authRequired_authenticated_shouldSucceed() throws Exception {
        when(shareLinkService.getShareLink(eq("xyz98765"), eq(USER_ID)))
                .thenReturn(Optional.of(authRequiredShare));

        mockMvc.perform(get(ApiPaths.SHARES + "/xyz98765"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("xyz98765"))
                .andExpect(jsonPath("$.requiresAuth").value(true));
    }

    @Test
    void getShareLink_notFound_shouldReturn404() throws Exception {
        when(shareLinkService.getShareLink(eq("notfound"), isNull()))
                .thenReturn(Optional.empty());

        mockMvc.perform(get(ApiPaths.SHARES + "/notfound"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void listUserShareLinks_authenticated_shouldSucceed() throws Exception {
        when(shareLinkService.getUserShareLinks(USER_ID))
                .thenReturn(List.of(publicShare, authRequiredShare));

        mockMvc.perform(get(ApiPaths.SHARES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("abc12345"))
                .andExpect(jsonPath("$[1].code").value("xyz98765"));

        verify(userService).ensureUserExists(USER_ID, "test@example.com");
    }

    @Test
    void listUserShareLinks_unauthenticated_shouldFail() throws Exception {
        mockMvc.perform(get(ApiPaths.SHARES))
                .andExpect(status().isUnauthorized());

        verify(shareLinkService, never()).getUserShareLinks(anyString());
    }

    @Test
    @WithMockUser
    void deleteShareLink_authenticated_shouldSucceed() throws Exception {
        doNothing().when(shareLinkService).deleteShareLink("abc12345", USER_ID);

        mockMvc.perform(delete(ApiPaths.SHARES + "/abc12345"))
                .andExpect(status().isNoContent());

        verify(shareLinkService).deleteShareLink("abc12345", USER_ID);
    }

    @Test
    void deleteShareLink_unauthenticated_shouldFail() throws Exception {
        mockMvc.perform(delete(ApiPaths.SHARES + "/abc12345"))
                .andExpect(status().isUnauthorized());

        verify(shareLinkService, never()).deleteShareLink(anyString(), anyString());
    }
}
