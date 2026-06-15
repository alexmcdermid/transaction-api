package com.transactionapi.model;

import com.transactionapi.constants.DashboardWidget;
import com.transactionapi.constants.Currency;
import com.transactionapi.constants.PnlDisplayMode;
import com.transactionapi.constants.ThemeMode;
import com.transactionapi.constants.TradeSortDirection;
import com.transactionapi.constants.TradeSortField;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "auth_id", nullable = false, unique = true, length = 128)
    private String authId;

    @Column(name = "email", length = 256)
    private String email;

    @Column(name = "premium", nullable = false)
    private boolean premium = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme_mode", length = 16)
    private ThemeMode themeMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "pnl_display_mode", length = 16)
    private PnlDisplayMode pnlDisplayMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_trade_sort_by", length = 32)
    private TradeSortField defaultTradeSortBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_trade_sort_direction", length = 8)
    private TradeSortDirection defaultTradeSortDirection;

    @Column(name = "show_trade_history", nullable = false)
    private boolean showTradeHistory = false;

    @Column(name = "dashboard_widgets", nullable = false, length = 256)
    private String dashboardWidgets = DashboardWidget.defaultStorageValue();

    @Enumerated(EnumType.STRING)
    @Column(name = "display_currency", nullable = false, length = 3)
    private Currency displayCurrency = Currency.USD;

    @Column(name = "tax_capital_gains_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxCapitalGainsRate = new BigDecimal("50.00");

    @Column(name = "tax_personal_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxPersonalRate = new BigDecimal("50.00");

    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

    @Column(name = "privacy_policy_accepted_at")
    private Instant privacyPolicyAcceptedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return Objects.requireNonNull(id);
    }

    public String getAuthId() {
        return authId;
    }

    public void setAuthId(String authId) {
        this.authId = authId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isPremium() {
        return premium;
    }

    public void setPremium(boolean premium) {
        this.premium = premium;
    }

    public ThemeMode getThemeMode() {
        return themeMode;
    }

    public void setThemeMode(ThemeMode themeMode) {
        this.themeMode = themeMode;
    }

    public PnlDisplayMode getPnlDisplayMode() {
        return pnlDisplayMode;
    }

    public void setPnlDisplayMode(PnlDisplayMode pnlDisplayMode) {
        this.pnlDisplayMode = pnlDisplayMode;
    }

    public TradeSortField getDefaultTradeSortBy() {
        return defaultTradeSortBy;
    }

    public void setDefaultTradeSortBy(TradeSortField defaultTradeSortBy) {
        this.defaultTradeSortBy = defaultTradeSortBy;
    }

    public TradeSortDirection getDefaultTradeSortDirection() {
        return defaultTradeSortDirection;
    }

    public void setDefaultTradeSortDirection(TradeSortDirection defaultTradeSortDirection) {
        this.defaultTradeSortDirection = defaultTradeSortDirection;
    }

    public boolean isShowTradeHistory() {
        return showTradeHistory;
    }

    public void setShowTradeHistory(boolean showTradeHistory) {
        this.showTradeHistory = showTradeHistory;
    }

    public String getDashboardWidgets() {
        return dashboardWidgets;
    }

    public void setDashboardWidgets(String dashboardWidgets) {
        this.dashboardWidgets = dashboardWidgets;
    }

    public Currency getDisplayCurrency() {
        return displayCurrency;
    }

    public void setDisplayCurrency(Currency displayCurrency) {
        this.displayCurrency = displayCurrency;
    }

    public BigDecimal getTaxCapitalGainsRate() {
        return taxCapitalGainsRate;
    }

    public void setTaxCapitalGainsRate(BigDecimal taxCapitalGainsRate) {
        this.taxCapitalGainsRate = taxCapitalGainsRate;
    }

    public BigDecimal getTaxPersonalRate() {
        return taxPersonalRate;
    }

    public void setTaxPersonalRate(BigDecimal taxPersonalRate) {
        this.taxPersonalRate = taxPersonalRate;
    }

    public Instant getTermsAcceptedAt() {
        return termsAcceptedAt;
    }

    public void setTermsAcceptedAt(Instant termsAcceptedAt) {
        this.termsAcceptedAt = termsAcceptedAt;
    }

    public Instant getPrivacyPolicyAcceptedAt() {
        return privacyPolicyAcceptedAt;
    }

    public void setPrivacyPolicyAcceptedAt(Instant privacyPolicyAcceptedAt) {
        this.privacyPolicyAcceptedAt = privacyPolicyAcceptedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
