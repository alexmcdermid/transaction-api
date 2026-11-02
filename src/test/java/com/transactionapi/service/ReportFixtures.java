package com.transactionapi.service;

import com.transactionapi.constants.AssetType;
import com.transactionapi.constants.Currency;
import com.transactionapi.constants.OptionType;
import com.transactionapi.constants.TradeDirection;
import com.transactionapi.model.Account;
import com.transactionapi.model.Trade;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

/** In-memory trades and accounts for pure report tests. Ids are normally assigned by Hibernate. */
final class ReportFixtures {

    private ReportFixtures() {
    }

    static TradeBuilder trade(String symbol) {
        return new TradeBuilder(symbol);
    }

    static Account account(String name, boolean taxFree) {
        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        account.setUserId("report-user");
        account.setName(name);
        account.setTaxFree(taxFree);
        return account;
    }

    static final class TradeBuilder {
        private final Trade trade = new Trade();
        private BigDecimal recordedRealizedPnl;

        private TradeBuilder(String symbol) {
            ReflectionTestUtils.setField(trade, "id", UUID.randomUUID());
            trade.setUserId("report-user");
            trade.setSymbol(symbol);
            trade.setAssetType(AssetType.STOCK);
            trade.setCurrency(Currency.USD);
            trade.setDirection(TradeDirection.LONG);
            trade.setQuantity(BigDecimal.ONE);
            trade.setEntryPrice(BigDecimal.ZERO);
            trade.setExitPrice(BigDecimal.ZERO);
            trade.setOpenedAt(LocalDate.of(2025, 6, 2));
            trade.setClosedAt(LocalDate.of(2025, 6, 2));
        }

        TradeBuilder id(String id) {
            ReflectionTestUtils.setField(trade, "id", UUID.fromString(id));
            return this;
        }

        TradeBuilder option(OptionType optionType, String strikePrice, LocalDate expiryDate) {
            trade.setAssetType(AssetType.OPTION);
            trade.setOptionType(optionType);
            trade.setStrikePrice(new BigDecimal(strikePrice));
            trade.setExpiryDate(expiryDate);
            return this;
        }

        TradeBuilder currency(Currency currency) {
            trade.setCurrency(currency);
            return this;
        }

        TradeBuilder direction(TradeDirection direction) {
            trade.setDirection(direction);
            return this;
        }

        TradeBuilder quantity(String quantity) {
            trade.setQuantity(new BigDecimal(quantity));
            return this;
        }

        TradeBuilder prices(String entryPrice, String exitPrice) {
            trade.setEntryPrice(new BigDecimal(entryPrice));
            trade.setExitPrice(new BigDecimal(exitPrice));
            return this;
        }

        TradeBuilder fees(String fees) {
            trade.setFees(new BigDecimal(fees));
            return this;
        }

        TradeBuilder marginRate(String marginRate) {
            trade.setMarginRate(new BigDecimal(marginRate));
            return this;
        }

        TradeBuilder openedOn(LocalDate openedAt) {
            trade.setOpenedAt(openedAt);
            return this;
        }

        TradeBuilder closedOn(LocalDate closedAt) {
            trade.setClosedAt(closedAt);
            return this;
        }

        TradeBuilder heldBetween(LocalDate openedAt, LocalDate closedAt) {
            trade.setOpenedAt(openedAt);
            trade.setClosedAt(closedAt);
            return this;
        }

        TradeBuilder account(Account account) {
            trade.setAccountId(account.getId());
            return this;
        }

        TradeBuilder notes(String notes) {
            trade.setNotes(notes);
            return this;
        }

        TradeBuilder auditTimestamps(Instant createdAt, Instant updatedAt) {
            ReflectionTestUtils.setField(trade, "createdAt", createdAt);
            ReflectionTestUtils.setField(trade, "updatedAt", updatedAt);
            return this;
        }

        /** Overrides the recorded realized P/L, which otherwise follows TradeService's formula. */
        TradeBuilder realizedPnl(String realizedPnl) {
            recordedRealizedPnl = new BigDecimal(realizedPnl);
            return this;
        }

        Trade build() {
            trade.setRealizedPnl(recordedRealizedPnl != null
                    ? recordedRealizedPnl
                    : TradeCalculations.grossPnl(trade)
                            .subtract(trade.getFees())
                            .subtract(TradeCalculations.marginInterest(trade))
                            .setScale(2, RoundingMode.HALF_UP));
            return trade;
        }
    }
}
