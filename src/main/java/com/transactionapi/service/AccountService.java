package com.transactionapi.service;

import com.transactionapi.dto.AccountRequest;
import com.transactionapi.model.Account;
import com.transactionapi.repository.AccountRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public List<Account> listAccounts(String userId) {
        return accountRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Account createAccount(String userId, AccountRequest request) {
        Account account = new Account();
        account.setUserId(userId);
        account.setName(request.name().trim());
        account.setDefaultStockFees(
                request.defaultStockFees() != null ? request.defaultStockFees() : BigDecimal.ZERO
        );
        account.setDefaultOptionFees(
                request.defaultOptionFees() != null ? request.defaultOptionFees() : BigDecimal.ZERO
        );
        account.setDefaultMarginRateUsd(
                request.defaultMarginRateUsd() != null ? request.defaultMarginRateUsd() : BigDecimal.ZERO
        );
        account.setDefaultMarginRateCad(
                request.defaultMarginRateCad() != null ? request.defaultMarginRateCad() : BigDecimal.ZERO
        );
        return accountRepository.save(account);
    }

    public void deleteAccount(String userId, UUID accountId) {
        Account account = accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        accountRepository.delete(account);
    }
}
