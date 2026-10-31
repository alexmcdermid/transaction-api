package com.transactionapi.service;

import com.transactionapi.model.Trade;

/**
 * A taxable loss paired with a same-symbol position opened within 30 days of the loss closing.
 * {@code daysFromLoss} is {@code match.openedAt - loss.closedAt}, signed.
 */
record RepurchaseMatch(Trade lossTrade, Trade matchTrade, long daysFromLoss, boolean possibleSamePosition) {
}
