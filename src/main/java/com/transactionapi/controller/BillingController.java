package com.transactionapi.controller;

import com.transactionapi.constants.ApiPaths;
import com.transactionapi.dto.TaxYearPassCheckoutResponse;
import com.transactionapi.dto.TaxYearPassResponse;
import com.transactionapi.model.User;
import com.transactionapi.security.UserIdResolver;
import com.transactionapi.service.StripeWebhookService;
import com.transactionapi.service.TaxYearPassService;
import com.transactionapi.service.UserService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.BILLING)
public class BillingController {

    private final TaxYearPassService taxYearPassService;
    private final StripeWebhookService stripeWebhookService;
    private final UserIdResolver userIdResolver;
    private final UserService userService;

    public BillingController(
            TaxYearPassService taxYearPassService,
            StripeWebhookService stripeWebhookService,
            UserIdResolver userIdResolver,
            UserService userService
    ) {
        this.taxYearPassService = taxYearPassService;
        this.stripeWebhookService = stripeWebhookService;
        this.userIdResolver = userIdResolver;
        this.userService = userService;
    }

    @GetMapping("/tax-year-passes")
    public List<TaxYearPassResponse> listTaxYearPasses(Authentication authentication) {
        String authId = userIdResolver.requireUserId(authentication);
        User user = userService.getOrCreateUser(authId, userIdResolver.resolveEmail(authentication));
        return taxYearPassService.listPasses(user).stream().map(TaxYearPassResponse::from).toList();
    }

    @PostMapping("/tax-year-passes/{taxYear}/checkout")
    public TaxYearPassCheckoutResponse startTaxYearPassCheckout(
            Authentication authentication,
            @PathVariable int taxYear
    ) {
        String authId = userIdResolver.requireUserId(authentication);
        String email = userIdResolver.resolveEmail(authentication);
        User user = userService.getOrCreateUser(authId, email);
        return new TaxYearPassCheckoutResponse(taxYearPassService.startCheckout(user, email, taxYear));
    }

    /** Public route: the Stripe-Signature header, verified against the raw body, is the authentication. */
    @PostMapping("/stripe/webhook")
    public ResponseEntity<Void> receiveStripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String stripeSignature
    ) {
        stripeWebhookService.handleWebhook(payload, stripeSignature);
        return ResponseEntity.ok().build();
    }
}
