package com.transactionapi.service;

import com.transactionapi.model.Account;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The caller's accounts by id, resolving report labels and tax treatment. Trades without an account
 * are labelled Unassigned and count as taxable.
 */
record ReportAccounts(Map<UUID, Account> accountsById) {

    private static final String UNASSIGNED_LABEL = "Unassigned";
    private static final String DELETED_ACCOUNT_LABEL = "Deleted account";

    static ReportAccounts of(List<Account> accounts) {
        return new ReportAccounts(accounts.stream().collect(Collectors.toMap(Account::getId, Function.identity())));
    }

    boolean owns(UUID accountId) {
        return accountsById.containsKey(accountId);
    }

    String name(UUID accountId) {
        if (accountId == null) {
            return UNASSIGNED_LABEL;
        }
        Account account = accountsById.get(accountId);
        return account != null ? account.getName() : DELETED_ACCOUNT_LABEL;
    }

    boolean isTaxFree(UUID accountId) {
        Account account = accountId != null ? accountsById.get(accountId) : null;
        return account != null && account.isTaxFree();
    }
}
