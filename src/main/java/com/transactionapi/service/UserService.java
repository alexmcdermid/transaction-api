package com.transactionapi.service;

import com.transactionapi.constants.DashboardWidget;
import com.transactionapi.constants.Currency;
import com.transactionapi.constants.PnlDisplayMode;
import com.transactionapi.constants.ThemeMode;
import com.transactionapi.constants.TradeSortDirection;
import com.transactionapi.constants.TradeSortField;
import com.transactionapi.model.User;
import com.transactionapi.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void ensureUserExists(String authId, String email) {
        getOrCreateUser(authId, email);
    }

    public User getOrCreateUser(String authId, String email) {
        return userRepository.findByAuthId(authId).map(existing -> {
            if (email != null && !email.isBlank() && !email.equalsIgnoreCase(existing.getEmail())) {
                existing.setEmail(email);
                return userRepository.save(existing);
            }
            return existing;
        }).orElseGet(() -> {
            User user = new User();
            user.setAuthId(authId);
            if (email != null && !email.isBlank()) {
                user.setEmail(email);
            }
            return userRepository.save(user);
        });
    }

    public User acceptLegalAgreement(String authId, String email) {
        User user = getOrCreateUser(authId, email);
        Instant now = Instant.now();
        user.setTermsAcceptedAt(now);
        user.setPrivacyPolicyAcceptedAt(now);
        return userRepository.save(user);
    }

    public boolean hasAcceptedLegalAgreement(User user) {
        return user.getTermsAcceptedAt() != null && user.getPrivacyPolicyAcceptedAt() != null;
    }

    public Optional<User> findByStripeCustomerId(String stripeCustomerId) {
        if (stripeCustomerId == null || stripeCustomerId.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByStripeCustomerId(stripeCustomerId);
    }

    public User updateStripeCustomerId(String authId, String email, String stripeCustomerId) {
        User user = getOrCreateUser(authId, email);
        if (stripeCustomerId != null && !stripeCustomerId.isBlank()) {
            user.setStripeCustomerId(stripeCustomerId);
        }
        return userRepository.save(user);
    }

    public Optional<User> updateStripeSubscriptionFromCheckout(
            String authId,
            String stripeCustomerId,
            String stripeSubscriptionId
    ) {
        if (authId == null || authId.isBlank()) {
            return updateStripeSubscriptionStatus(stripeCustomerId, stripeSubscriptionId, "checkout_completed");
        }
        User user = getOrCreateUser(authId, null);
        applyStripeSubscription(user, stripeCustomerId, stripeSubscriptionId, "checkout_completed", true);
        return Optional.of(userRepository.save(user));
    }

    public Optional<User> updateStripeSubscriptionStatus(
            String stripeCustomerId,
            String stripeSubscriptionId,
            String status
    ) {
        Optional<User> user = Optional.empty();
        if (stripeCustomerId != null && !stripeCustomerId.isBlank()) {
            user = userRepository.findByStripeCustomerId(stripeCustomerId);
        }
        if (user.isEmpty() && stripeSubscriptionId != null && !stripeSubscriptionId.isBlank()) {
            user = userRepository.findByStripeSubscriptionId(stripeSubscriptionId);
        }
        return user.map(existing -> {
            applyStripeSubscription(
                    existing,
                    stripeCustomerId,
                    stripeSubscriptionId,
                    status,
                    isPremiumSubscriptionStatus(status)
            );
            return userRepository.save(existing);
        });
    }

    private void applyStripeSubscription(
            User user,
            String stripeCustomerId,
            String stripeSubscriptionId,
            String status,
            boolean premium
    ) {
        if (stripeCustomerId != null && !stripeCustomerId.isBlank()) {
            user.setStripeCustomerId(stripeCustomerId);
        }
        if (stripeSubscriptionId != null && !stripeSubscriptionId.isBlank()) {
            user.setStripeSubscriptionId(stripeSubscriptionId);
        }
        if (status != null && !status.isBlank()) {
            user.setStripeSubscriptionStatus(status);
        }
        user.setPremium(premium);
    }

    private boolean isPremiumSubscriptionStatus(String status) {
        return "active".equals(status) || "trialing".equals(status) || "checkout_completed".equals(status);
    }

    public User updatePreferences(
            String authId,
            String email,
            ThemeMode themeMode,
            PnlDisplayMode pnlDisplayMode,
            TradeSortField defaultTradeSortBy,
            TradeSortDirection defaultTradeSortDirection,
            Boolean showTradeHistory,
            Boolean showDetailedTradeTimes,
            List<DashboardWidget> dashboardWidgets,
            Currency displayCurrency,
            BigDecimal taxCapitalGainsRate,
            BigDecimal taxPersonalRate
    ) {
        User user = getOrCreateUser(authId, email);
        if (themeMode != null) {
            user.setThemeMode(themeMode);
        }
        if (pnlDisplayMode != null) {
            user.setPnlDisplayMode(pnlDisplayMode);
        }
        if (defaultTradeSortBy != null) {
            user.setDefaultTradeSortBy(defaultTradeSortBy);
        }
        if (defaultTradeSortDirection != null) {
            user.setDefaultTradeSortDirection(defaultTradeSortDirection);
        }
        if (showTradeHistory != null) {
            user.setShowTradeHistory(showTradeHistory);
        }
        if (showDetailedTradeTimes != null) {
            user.setShowDetailedTradeTimes(showDetailedTradeTimes);
        }
        if (dashboardWidgets != null) {
            user.setDashboardWidgets(DashboardWidget.toStorage(dashboardWidgets));
        }
        if (displayCurrency != null) {
            user.setDisplayCurrency(displayCurrency);
        }
        if (taxCapitalGainsRate != null) {
            user.setTaxCapitalGainsRate(taxCapitalGainsRate.setScale(2, RoundingMode.HALF_UP));
        }
        if (taxPersonalRate != null) {
            user.setTaxPersonalRate(taxPersonalRate.setScale(2, RoundingMode.HALF_UP));
        }
        return userRepository.save(user);
    }
}
