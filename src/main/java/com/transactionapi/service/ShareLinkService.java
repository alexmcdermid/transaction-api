package com.transactionapi.service;

import com.transactionapi.constants.ShareType;
import com.transactionapi.model.ShareLink;
import com.transactionapi.repository.ShareLinkRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ShareLinkService {

    private static final String CODE_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 8;
    private static final int MAX_CODE_GENERATION_ATTEMPTS = 10;
    private static final long MAX_EXPIRY_DAYS = 90;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ShareLinkRepository shareLinkRepository;

    public ShareLinkService(ShareLinkRepository shareLinkRepository) {
        this.shareLinkRepository = shareLinkRepository;
    }

    public ShareLink createShareLink(
            String userId,
            ShareType shareType,
            String data,
            boolean requiresAuth,
            Long expiryDays
    ) {
        if (expiryDays == null || expiryDays < 1) {
            expiryDays = 7L;
        }
        if (expiryDays > MAX_EXPIRY_DAYS) {
            expiryDays = MAX_EXPIRY_DAYS;
        }

        Instant expiresAt = Instant.now().plus(expiryDays, ChronoUnit.DAYS);
        String code = generateUniqueCode();

        ShareLink shareLink = new ShareLink();
        shareLink.setCode(code);
        shareLink.setUserId(userId);
        shareLink.setShareType(shareType);
        shareLink.setData(data);
        shareLink.setRequiresAuth(requiresAuth);
        shareLink.setExpiresAt(expiresAt);

        return shareLinkRepository.save(shareLink);
    }

    public ShareLink findByCodeRaw(String code) {
        return shareLinkRepository.findByCode(code)
                .filter(link -> !link.getExpiresAt().isBefore(Instant.now()))
                .orElse(null);
    }

    public Optional<ShareLink> getShareLink(String code, String requestingUserId) {
        return shareLinkRepository.findByCode(code)
                .filter(link -> !link.getExpiresAt().isBefore(Instant.now()))
                .filter(link -> {
                    if (!link.isRequiresAuth()) {
                        return true;
                    }
                    return requestingUserId != null && !requestingUserId.isBlank();
                })
                .map(link -> {
                    link.setAccessCount(link.getAccessCount() + 1);
                    return shareLinkRepository.save(link);
                });
    }

    public List<ShareLink> getUserShareLinks(String userId) {
        return shareLinkRepository.findByUserId(userId);
    }

    public void deleteShareLink(String code, String userId) {
        shareLinkRepository.findByCode(code)
                .filter(link -> link.getUserId().equals(userId))
                .ifPresent(shareLinkRepository::delete);
    }

    public int deleteExpiredShares() {
        return shareLinkRepository.deleteExpired(Instant.now());
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
            String code = generateCode();
            if (shareLinkRepository.findByCode(code).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException(
            "Failed to generate unique share code after " + MAX_CODE_GENERATION_ATTEMPTS + 
            " attempts. Consider increasing MAX_CODE_GENERATION_ATTEMPTS or expanding CODE_CHARS if collisions are frequent."
        );
    }

    private String generateCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return code.toString();
    }
}
