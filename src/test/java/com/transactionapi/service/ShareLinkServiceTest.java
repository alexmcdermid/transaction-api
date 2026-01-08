package com.transactionapi.service;

import com.transactionapi.constants.ShareType;
import com.transactionapi.model.ShareLink;
import com.transactionapi.repository.ShareLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShareLinkServiceTest {

    @Mock
    private ShareLinkRepository shareLinkRepository;

    @InjectMocks
    private ShareLinkService shareLinkService;

    private ShareLink publicShare;
    private ShareLink authRequiredShare;
    private ShareLink expiredShare;

    @BeforeEach
    void setUp() {
        publicShare = new ShareLink();
        publicShare.setCode("abc12345");
        publicShare.setUserId("user1");
        publicShare.setShareType(ShareType.SUMMARY);
        publicShare.setData("{\"test\":\"data\"}");
        publicShare.setRequiresAuth(false);
        publicShare.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        publicShare.setAccessCount(0);

        authRequiredShare = new ShareLink();
        authRequiredShare.setCode("xyz98765");
        authRequiredShare.setUserId("user1");
        authRequiredShare.setShareType(ShareType.TRADES);
        authRequiredShare.setData("{\"test\":\"auth\"}");
        authRequiredShare.setRequiresAuth(true);
        authRequiredShare.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        authRequiredShare.setAccessCount(0);

        expiredShare = new ShareLink();
        expiredShare.setCode("old12345");
        expiredShare.setUserId("user1");
        expiredShare.setShareType(ShareType.SUMMARY);
        expiredShare.setData("{\"test\":\"expired\"}");
        expiredShare.setRequiresAuth(false);
        expiredShare.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        expiredShare.setAccessCount(0);
    }

    @Test
    void createShareLink_shouldGenerateUniqueCode() {
        when(shareLinkRepository.findByCode(anyString())).thenReturn(Optional.empty());
        when(shareLinkRepository.save(any(ShareLink.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShareLink result = shareLinkService.createShareLink(
                "user1",
                ShareType.SUMMARY,
                "{\"test\":\"data\"}",
                false,
                7L
        );

        assertThat(result.getCode()).hasSize(8);
        assertThat(result.getUserId()).isEqualTo("user1");
        assertThat(result.getShareType()).isEqualTo(ShareType.SUMMARY);
        assertThat(result.getData()).isEqualTo("{\"test\":\"data\"}");
        assertThat(result.isRequiresAuth()).isFalse();
        verify(shareLinkRepository, times(1)).save(any(ShareLink.class));
    }

    @Test
    void createShareLink_shouldEnforceMaxExpiry() {
        when(shareLinkRepository.findByCode(anyString())).thenReturn(Optional.empty());
        when(shareLinkRepository.save(any(ShareLink.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShareLink result = shareLinkService.createShareLink(
                "user1",
                ShareType.SUMMARY,
                "{\"test\":\"data\"}",
                false,
                100L // Exceeds max of 90
        );

        Instant maxExpiry = Instant.now().plus(90, ChronoUnit.DAYS);
        assertThat(result.getExpiresAt()).isBefore(maxExpiry.plus(1, ChronoUnit.SECONDS));
        assertThat(result.getExpiresAt()).isAfter(maxExpiry.minus(1, ChronoUnit.SECONDS));
    }

    @Test
    void createShareLink_shouldDefaultTo7DaysWhenNullExpiry() {
        when(shareLinkRepository.findByCode(anyString())).thenReturn(Optional.empty());
        when(shareLinkRepository.save(any(ShareLink.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShareLink result = shareLinkService.createShareLink(
                "user1",
                ShareType.SUMMARY,
                "{\"test\":\"data\"}",
                false,
                null
        );

        Instant expectedExpiry = Instant.now().plus(7, ChronoUnit.DAYS);
        assertThat(result.getExpiresAt()).isBefore(expectedExpiry.plus(1, ChronoUnit.SECONDS));
        assertThat(result.getExpiresAt()).isAfter(expectedExpiry.minus(1, ChronoUnit.SECONDS));
    }

    @Test
    void getShareLink_publicShare_shouldReturnForAnyone() {
        when(shareLinkRepository.findByCode("abc12345")).thenReturn(Optional.of(publicShare));
        when(shareLinkRepository.save(any(ShareLink.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<ShareLink> result = shareLinkService.getShareLink("abc12345", null);

        assertThat(result).isPresent();
        assertThat(result.get().getCode()).isEqualTo("abc12345");
        assertThat(result.get().getAccessCount()).isEqualTo(1);
        verify(shareLinkRepository).save(any(ShareLink.class));
    }

    @Test
    void getShareLink_authRequired_shouldReturnForAuthenticatedUser() {
        when(shareLinkRepository.findByCode("xyz98765")).thenReturn(Optional.of(authRequiredShare));
        when(shareLinkRepository.save(any(ShareLink.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<ShareLink> result = shareLinkService.getShareLink("xyz98765", "user1");

        assertThat(result).isPresent();
        assertThat(result.get().getCode()).isEqualTo("xyz98765");
        assertThat(result.get().getAccessCount()).isEqualTo(1);
    }

    @Test
    void getShareLink_authRequired_shouldNotReturnForUnauthenticated() {
        when(shareLinkRepository.findByCode("xyz98765")).thenReturn(Optional.of(authRequiredShare));

        Optional<ShareLink> result = shareLinkService.getShareLink("xyz98765", null);

        assertThat(result).isEmpty();
        verify(shareLinkRepository, never()).save(any(ShareLink.class));
    }

    @Test
    void getShareLink_expired_shouldNotReturn() {
        when(shareLinkRepository.findByCode("old12345")).thenReturn(Optional.of(expiredShare));

        Optional<ShareLink> result = shareLinkService.getShareLink("old12345", "user1");

        assertThat(result).isEmpty();
        verify(shareLinkRepository, never()).save(any(ShareLink.class));
    }

    @Test
    void getShareLink_notFound_shouldReturnEmpty() {
        when(shareLinkRepository.findByCode("notfound")).thenReturn(Optional.empty());

        Optional<ShareLink> result = shareLinkService.getShareLink("notfound", "user1");

        assertThat(result).isEmpty();
    }

    @Test
    void getUserShareLinks_shouldReturnUserShares() {
        when(shareLinkRepository.findByUserId("user1")).thenReturn(List.of(publicShare, authRequiredShare));

        List<ShareLink> result = shareLinkService.getUserShareLinks("user1");

        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(publicShare, authRequiredShare);
    }

    @Test
    void deleteShareLink_shouldDeleteWhenUserMatches() {
        when(shareLinkRepository.findByCode("abc12345")).thenReturn(Optional.of(publicShare));

        shareLinkService.deleteShareLink("abc12345", "user1");

        verify(shareLinkRepository).delete(publicShare);
    }

    @Test
    void deleteShareLink_shouldNotDeleteWhenUserDoesNotMatch() {
        when(shareLinkRepository.findByCode("abc12345")).thenReturn(Optional.of(publicShare));

        shareLinkService.deleteShareLink("abc12345", "user2");

        verify(shareLinkRepository, never()).delete(any(ShareLink.class));
    }

    @Test
    void deleteExpiredShares_shouldCallRepository() {
        when(shareLinkRepository.deleteExpired(any(Instant.class))).thenReturn(5);

        int deleted = shareLinkService.deleteExpiredShares();

        assertThat(deleted).isEqualTo(5);
        verify(shareLinkRepository).deleteExpired(any(Instant.class));
    }

    @Test
    void findByCodeRaw_shouldReturnNonExpired() {
        when(shareLinkRepository.findByCode("abc12345")).thenReturn(Optional.of(publicShare));

        ShareLink result = shareLinkService.findByCodeRaw("abc12345");

        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo("abc12345");
    }

    @Test
    void findByCodeRaw_shouldNotReturnExpired() {
        when(shareLinkRepository.findByCode("old12345")).thenReturn(Optional.of(expiredShare));

        ShareLink result = shareLinkService.findByCodeRaw("old12345");

        assertThat(result).isNull();
    }
}
