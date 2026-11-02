package com.transactionapi.service;

import com.transactionapi.config.BillingSettings;
import com.transactionapi.constants.TaxYearPassStatus;
import com.transactionapi.model.TaxYearPass;
import com.transactionapi.model.User;
import com.transactionapi.repository.TaxYearPassRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Owns tax year pass entitlements. A user may read reports and exports for a tax year only while
 * they hold a PAID pass for that year. Entitlement is computed from pass rows; nothing else is a
 * source of truth.
 */
@Service
@Transactional
public class TaxYearPassService {

    public static final int EARLIEST_TAX_YEAR = 2000;

    /** Display precedence when a user has several passes for one year. */
    private static final List<TaxYearPassStatus> STATUS_PRECEDENCE = List.of(
            TaxYearPassStatus.PAID,
            TaxYearPassStatus.PROCESSING,
            TaxYearPassStatus.PENDING,
            TaxYearPassStatus.FAILED,
            TaxYearPassStatus.REFUNDED
    );

    private static final List<TaxYearPassStatus> REUSABLE_FOR_CHECKOUT = List.of(
            TaxYearPassStatus.PENDING,
            TaxYearPassStatus.FAILED
    );

    private static final Set<TaxYearPassStatus> CHECKOUT_SESSION_TARGETS = EnumSet.of(
            TaxYearPassStatus.PROCESSING,
            TaxYearPassStatus.PAID,
            TaxYearPassStatus.FAILED
    );

    private final TaxYearPassRepository taxYearPassRepository;
    private final BillingSettings billingSettings;

    public TaxYearPassService(TaxYearPassRepository taxYearPassRepository, BillingSettings billingSettings) {
        this.taxYearPassRepository = taxYearPassRepository;
        this.billingSettings = billingSettings;
    }

    @Transactional(readOnly = true)
    public List<TaxYearPass> listPasses(User user) {
        return taxYearPassRepository.findByUserIdOrderByTaxYearDescCreatedAtDesc(user.getId());
    }

    @Transactional(readOnly = true)
    public boolean hasPaidPass(User user, int taxYear) {
        return taxYearPassRepository.existsByUserIdAndTaxYearAndStatus(user.getId(), taxYear, TaxYearPassStatus.PAID);
    }

    /** Authoritative gate for every tax year report and export. */
    @Transactional(readOnly = true)
    public void requirePaidPass(User user, int taxYear) {
        if (!hasPaidPass(user, taxYear)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tax year pass required for " + taxYear);
        }
    }

    /** The most significant pass status per tax year, e.g. PAID wins over an abandoned PENDING. */
    @Transactional(readOnly = true)
    public Map<Integer, TaxYearPassStatus> effectiveStatusByYear(User user) {
        Map<Integer, TaxYearPassStatus> statuses = new HashMap<>();
        for (TaxYearPass pass : listPasses(user)) {
            statuses.merge(pass.getTaxYear(), pass.getStatus(), TaxYearPassService::moreSignificant);
        }
        return statuses;
    }

    public int currentTaxYear() {
        return Year.now().getValue();
    }

    /** Tax years follow the calendar year of each trade's close date. */
    public void requireValidTaxYear(int taxYear) {
        int currentTaxYear = currentTaxYear();
        if (taxYear < EARLIEST_TAX_YEAR || taxYear > currentTaxYear) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tax year must be between " + EARLIEST_TAX_YEAR + " and " + currentTaxYear
            );
        }
    }

    /**
     * Returns the Payment Link URL that buys {@code taxYear}. The pass id travels as Stripe's
     * client_reference_id so the webhook can find the pass once the customer pays.
     */
    public String startCheckout(User user, String email, int taxYear) {
        requireValidTaxYear(taxYear);
        if (!billingSettings.isCheckoutEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Tax year pass checkout is not configured");
        }
        UUID userId = user.getId();
        if (taxYearPassRepository.existsByUserIdAndTaxYearAndStatus(userId, taxYear, TaxYearPassStatus.PAID)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tax year " + taxYear + " is already unlocked");
        }
        if (taxYearPassRepository.existsByUserIdAndTaxYearAndStatus(userId, taxYear, TaxYearPassStatus.PROCESSING)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A payment for tax year " + taxYear + " is still processing"
            );
        }
        TaxYearPass pass = taxYearPassRepository
                .findFirstByUserIdAndTaxYearAndStatusInOrderByCreatedAtDesc(userId, taxYear, REUSABLE_FOR_CHECKOUT)
                .orElseGet(() -> taxYearPassRepository.save(newPendingPass(userId, taxYear)));
        return paymentLinkUrl(pass.getId(), email);
    }

    /**
     * Applies a checkout session webhook to the pass named by its client_reference_id: moves it to
     * {@code targetStatus} (PROCESSING, PAID or FAILED) and records what Stripe charged. Transitions
     * that {@link TaxYearPassStatus#canTransitionTo} rejects are no-ops, so replayed and out-of-order
     * deliveries are harmless.
     */
    public WebhookUpdate applyCheckoutSession(
            UUID passId,
            TaxYearPassStatus targetStatus,
            CheckoutSessionDetails session
    ) {
        if (!CHECKOUT_SESSION_TARGETS.contains(targetStatus)) {
            throw new IllegalArgumentException("A checkout session cannot move a tax year pass to " + targetStatus);
        }
        Optional<TaxYearPass> lockedPass = taxYearPassRepository.findByIdForUpdate(passId);
        if (lockedPass.isEmpty()) {
            return new WebhookUpdate(passId, WebhookOutcome.PASS_NOT_FOUND, null);
        }
        TaxYearPass pass = lockedPass.get();
        TaxYearPassStatus currentStatus = pass.getStatus();
        boolean sameSession = Objects.equals(pass.getStripeCheckoutSessionId(), session.checkoutSessionId());
        if (!currentStatus.canTransitionTo(targetStatus)) {
            boolean paymentNotApplied = targetStatus == TaxYearPassStatus.PAID && !sameSession;
            return new WebhookUpdate(
                    passId,
                    paymentNotApplied ? WebhookOutcome.DUPLICATE_PAYMENT : WebhookOutcome.UNCHANGED,
                    currentStatus
            );
        }
        // canTransitionTo cannot see sessions, so it lets two stale deliveries through. FAILED -> PROCESSING is for
        // a retry with a new session, not the failed session reporting "unpaid" late. A PROCESSING pass waits on
        // the session that moved it there, so a failure from another session (a redelivered earlier attempt, a
        // second tab) must not reopen checkout while that payment clears.
        boolean lateUnpaidForFailedSession = sameSession
                && currentStatus == TaxYearPassStatus.FAILED
                && targetStatus == TaxYearPassStatus.PROCESSING;
        boolean failureFromAnotherSession = !sameSession
                && currentStatus == TaxYearPassStatus.PROCESSING
                && targetStatus == TaxYearPassStatus.FAILED;
        if (lateUnpaidForFailedSession || failureFromAnotherSession) {
            return new WebhookUpdate(passId, WebhookOutcome.UNCHANGED, currentStatus);
        }
        pass.setStatus(targetStatus);
        pass.setStripeCheckoutSessionId(session.checkoutSessionId());
        pass.setStripePaymentIntentId(session.paymentIntentId());
        pass.setStripeCustomerId(session.customerId());
        pass.setAmountTotal(session.amountTotal());
        pass.setCurrency(session.currency() == null ? null : session.currency().toUpperCase(Locale.ROOT));
        if (targetStatus == TaxYearPassStatus.PAID) {
            pass.setPaidAt(Instant.now());
        }
        return new WebhookUpdate(passId, WebhookOutcome.UPDATED, targetStatus);
    }

    /** A fully refunded payment revokes the pass it paid for. An empty result means no pass matched. */
    public List<WebhookUpdate> markRefunded(String paymentIntentId) {
        List<WebhookUpdate> updates = new ArrayList<>();
        for (TaxYearPass pass : taxYearPassRepository.findByStripePaymentIntentIdForUpdate(paymentIntentId)) {
            if (pass.getStatus().canTransitionTo(TaxYearPassStatus.REFUNDED)) {
                pass.setStatus(TaxYearPassStatus.REFUNDED);
                pass.setRefundedAt(Instant.now());
                updates.add(new WebhookUpdate(pass.getId(), WebhookOutcome.UPDATED, TaxYearPassStatus.REFUNDED));
            } else {
                updates.add(new WebhookUpdate(pass.getId(), WebhookOutcome.UNCHANGED, pass.getStatus()));
            }
        }
        return updates;
    }

    private String paymentLinkUrl(UUID passId, String email) {
        UriComponentsBuilder url = UriComponentsBuilder.fromUriString(billingSettings.getPaymentLinkUrl())
                .queryParam("client_reference_id", passId);
        if (StringUtils.hasText(email)) {
            // URLEncoder turns '+' into %2B; a bare '+' would reach Stripe as a space.
            url.queryParam("prefilled_email", URLEncoder.encode(email, StandardCharsets.UTF_8));
        }
        return url.build(true).toUriString();
    }

    private static TaxYearPass newPendingPass(UUID userId, int taxYear) {
        TaxYearPass pass = new TaxYearPass();
        pass.setUserId(userId);
        pass.setTaxYear(taxYear);
        pass.setStatus(TaxYearPassStatus.PENDING);
        return pass;
    }

    private static TaxYearPassStatus moreSignificant(TaxYearPassStatus left, TaxYearPassStatus right) {
        return STATUS_PRECEDENCE.indexOf(left) <= STATUS_PRECEDENCE.indexOf(right) ? left : right;
    }

    /** What Stripe reported for a checkout session. {@code amountTotal} is in minor units. */
    public record CheckoutSessionDetails(
            String checkoutSessionId,
            String paymentIntentId,
            String customerId,
            Long amountTotal,
            String currency
    ) {
    }

    /** What one webhook delivery did to one pass. {@code status} is null when the pass was not found. */
    public record WebhookUpdate(UUID passId, WebhookOutcome outcome, TaxYearPassStatus status) {
    }

    public enum WebhookOutcome {
        UPDATED,
        /** Replayed, stale or out-of-order delivery; the pass was left as it was. */
        UNCHANGED,
        /** A different checkout session paid for a pass that is already PAID or REFUNDED. Refund it in Stripe. */
        DUPLICATE_PAYMENT,
        PASS_NOT_FOUND
    }
}
