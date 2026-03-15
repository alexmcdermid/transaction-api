package com.transactionapi.model;

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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
