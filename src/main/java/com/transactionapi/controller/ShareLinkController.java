package com.transactionapi.controller;

import com.transactionapi.constants.ApiPaths;
import com.transactionapi.dto.CreateShareLinkRequest;
import com.transactionapi.dto.ShareLinkResponse;
import com.transactionapi.model.ShareLink;
import com.transactionapi.security.UserIdResolver;
import com.transactionapi.service.ShareLinkService;
import com.transactionapi.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.SHARES)
public class ShareLinkController {

    private final ShareLinkService shareLinkService;
    private final UserIdResolver userIdResolver;
    private final UserService userService;

    public ShareLinkController(
            ShareLinkService shareLinkService,
            UserIdResolver userIdResolver,
            UserService userService
    ) {
        this.shareLinkService = shareLinkService;
        this.userIdResolver = userIdResolver;
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<ShareLinkResponse> createShareLink(
            Authentication authentication,
            @Valid @RequestBody CreateShareLinkRequest request
    ) {
        String userId = userIdResolver.requireUserId(authentication);
        userService.ensureUserExists(userId, userIdResolver.resolveEmail(authentication));

        ShareLink shareLink = shareLinkService.createShareLink(
                userId,
                request.shareType(),
                request.data(),
                request.requiresAuth(),
                request.expiryDays()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(toResponse(shareLink));
    }

    @GetMapping("/{code}")
    public ResponseEntity<ShareLinkResponse> getShareLink(
            Authentication authentication,
            @PathVariable String code
    ) {
        String requestingUserId = null;
        try {
            requestingUserId = userIdResolver.requireUserId(authentication);
        } catch (Exception e) {
            ShareLink shareLink = shareLinkService.findByCodeRaw(code);
            if (shareLink != null && shareLink.isRequiresAuth()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }

        return shareLinkService.getShareLink(code, requestingUserId)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<ShareLinkResponse> listUserShareLinks(Authentication authentication) {
        String userId = userIdResolver.requireUserId(authentication);
        userService.ensureUserExists(userId, userIdResolver.resolveEmail(authentication));

        return shareLinkService.getUserShareLinks(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> deleteShareLink(
            Authentication authentication,
            @PathVariable String code
    ) {
        String userId = userIdResolver.requireUserId(authentication);
        shareLinkService.deleteShareLink(code, userId);
        return ResponseEntity.noContent().build();
    }

    private ShareLinkResponse toResponse(ShareLink shareLink) {
        return new ShareLinkResponse(
                shareLink.getCode(),
                shareLink.getShareType(),
                shareLink.getData(),
                shareLink.isRequiresAuth(),
                shareLink.getExpiresAt(),
                shareLink.getAccessCount(),
                shareLink.getCreatedAt()
        );
    }
}
