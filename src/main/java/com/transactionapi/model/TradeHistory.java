package com.transactionapi.model;

import com.transactionapi.constants.AssetType;
import com.transactionapi.constants.Currency;
import com.transactionapi.constants.OptionType;
import com.transactionapi.constants.TradeDirection;
import com.transactionapi.constants.TradeHistoryAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.lang.NonNull;

@Entity
@Table(name = "trade_history")
public class TradeHistory {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "trade_id", nullable = false)
    private UUID tradeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TradeHistoryAction action;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(nullable = false, length = 12)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 10)
    private AssetType assetType;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3)
    private Currency currency = Currency.USD;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TradeDirection direction;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "entry_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal entryPrice;

    @Column(name = "exit_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal exitPrice;

    @Column(precision = 18, scale = 2, nullable = false)
    private BigDecimal fees = BigDecimal.ZERO;

    @Column(name = "margin_rate", precision = 8, scale = 4, nullable = false)
    private BigDecimal marginRate = BigDecimal.ZERO;

    @Column(name = "account_id")
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "option_type", length = 10)
    private OptionType optionType;

    @Column(name = "strike_price", precision = 18, scale = 4)
    private BigDecimal strikePrice;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "opened_at", nullable = false)
    private LocalDate openedAt;

    @Column(name = "closed_at", nullable = false)
    private LocalDate closedAt;

    @Column(name = "realized_pnl", precision = 18, scale = 2, nullable = false)
    private BigDecimal realizedPnl = BigDecimal.ZERO;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "trade_created_at", nullable = false)
    private Instant tradeCreatedAt;

    @Column(name = "trade_updated_at", nullable = false)
    private Instant tradeUpdatedAt;

    @Column(name = "action_at", nullable = false)
    private Instant actionAt;

    @PrePersist
    void onCreate() {
        if (this.actionAt == null) {
            this.actionAt = Instant.now();
        }
    }

    public static TradeHistory fromTrade(Trade trade, TradeHistoryAction action) {
        TradeHistory history = new TradeHistory();
        history.tradeId = trade.getId();
        history.action = action;
        history.userId = trade.getUserId();
        history.symbol = trade.getSymbol();
        history.assetType = trade.getAssetType();
        history.currency = trade.getCurrency();
        history.direction = trade.getDirection();
        history.quantity = trade.getQuantity();
        history.entryPrice = trade.getEntryPrice();
        history.exitPrice = trade.getExitPrice();
        history.fees = trade.getFees();
        history.marginRate = trade.getMarginRate();
        history.accountId = trade.getAccountId();
        history.optionType = trade.getOptionType();
        history.strikePrice = trade.getStrikePrice();
        history.expiryDate = trade.getExpiryDate();
        history.openedAt = trade.getOpenedAt();
        history.closedAt = trade.getClosedAt();
        history.realizedPnl = trade.getRealizedPnl();
        history.notes = trade.getNotes();
        history.tradeCreatedAt = trade.getCreatedAt();
        history.tradeUpdatedAt = trade.getUpdatedAt();
        return history;
    }

    @NonNull
    public UUID getId() {
        return Objects.requireNonNull(id);
    }

    public UUID getTradeId() {
        return tradeId;
    }

    public TradeHistoryAction getAction() {
        return action;
    }

    public String getUserId() {
        return userId;
    }

    public String getSymbol() {
        return symbol;
    }

    public AssetType getAssetType() {
        return assetType;
    }

    public Currency getCurrency() {
        return currency;
    }

    public TradeDirection getDirection() {
        return direction;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getEntryPrice() {
        return entryPrice;
    }

    public BigDecimal getExitPrice() {
        return exitPrice;
    }

    public BigDecimal getFees() {
        return fees;
    }

    public BigDecimal getMarginRate() {
        return marginRate;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public OptionType getOptionType() {
        return optionType;
    }

    public BigDecimal getStrikePrice() {
        return strikePrice;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public LocalDate getOpenedAt() {
        return openedAt;
    }

    public LocalDate getClosedAt() {
        return closedAt;
    }

    public BigDecimal getRealizedPnl() {
        return realizedPnl;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getTradeCreatedAt() {
        return tradeCreatedAt;
    }

    public Instant getTradeUpdatedAt() {
        return tradeUpdatedAt;
    }

    public Instant getActionAt() {
        return actionAt;
    }
}
