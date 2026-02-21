package com.transactionapi.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.springframework.lang.NonNull;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "default_stock_fees", precision = 18, scale = 2, nullable = false)
    private BigDecimal defaultStockFees = BigDecimal.ZERO;

    @Column(name = "default_option_fees", precision = 18, scale = 2, nullable = false)
    private BigDecimal defaultOptionFees = BigDecimal.ZERO;

    @Column(name = "default_margin_rate_usd", precision = 8, scale = 4, nullable = false)
    private BigDecimal defaultMarginRateUsd = BigDecimal.ZERO;

    @Column(name = "default_margin_rate_cad", precision = 8, scale = 4, nullable = false)
    private BigDecimal defaultMarginRateCad = BigDecimal.ZERO;

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

    @NonNull
    public UUID getId() {
        return Objects.requireNonNull(id);
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getDefaultStockFees() {
        return defaultStockFees;
    }

    public void setDefaultStockFees(BigDecimal defaultStockFees) {
        this.defaultStockFees = defaultStockFees;
    }

    public BigDecimal getDefaultOptionFees() {
        return defaultOptionFees;
    }

    public void setDefaultOptionFees(BigDecimal defaultOptionFees) {
        this.defaultOptionFees = defaultOptionFees;
    }

    public BigDecimal getDefaultMarginRateUsd() {
        return defaultMarginRateUsd;
    }

    public void setDefaultMarginRateUsd(BigDecimal defaultMarginRateUsd) {
        this.defaultMarginRateUsd = defaultMarginRateUsd;
    }

    public BigDecimal getDefaultMarginRateCad() {
        return defaultMarginRateCad;
    }

    public void setDefaultMarginRateCad(BigDecimal defaultMarginRateCad) {
        this.defaultMarginRateCad = defaultMarginRateCad;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
