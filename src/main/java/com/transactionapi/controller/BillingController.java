package com.transactionapi.controller;

import com.transactionapi.constants.ApiPaths;
import com.transactionapi.dto.BillingSessionResponse;
import com.transactionapi.security.UserIdResolver;
import com.transactionapi.service.StripeBillingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.BILLING)
public class BillingController {

    private final StripeBillingService stripeBillingService;
    private final UserIdResolver userIdResolver;

    public BillingController(StripeBillingService stripeBillingService, UserIdResolver userIdResolver) {
        this.stripeBillingService = stripeBillingService;
        this.userIdResolver = userIdResolver;
    }

    @PostMapping("/checkout-session")
    public BillingSessionResponse createCheckoutSession(Authentication authentication) {
        String authId = userIdResolver.requireUserId(authentication);
        String email = userIdResolver.resolveEmail(authentication);
        return new BillingSessionResponse(stripeBillingService.createCheckoutSession(authId, email));
    }

    @PostMapping("/portal-session")
    public BillingSessionResponse createPortalSession(Authentication authentication) {
        String authId = userIdResolver.requireUserId(authentication);
        String email = userIdResolver.resolveEmail(authentication);
        return new BillingSessionResponse(stripeBillingService.createBillingPortalSession(authId, email));
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signature
    ) {
        stripeBillingService.handleWebhook(payload, signature);
        return ResponseEntity.ok().build();
    }
}
