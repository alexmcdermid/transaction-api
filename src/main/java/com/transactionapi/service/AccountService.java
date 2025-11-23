package com.transactionapi.service;

import com.transactionapi.model.Account;
import com.transactionapi.dto.AccountResponse;
import com.transactionapi.dto.CreateAccountRequest;
import com.transactionapi.repository.AccountRepository;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public AccountResponse createAccount(CreateAccountRequest request, String userId) {
        Account account = new Account();
        account.setUserId(userId);
        account.setName(request.name());
        account.setInstitution(request.institution());
        account.setType(request.type());
        account.setCurrency(request.currency().toUpperCase(Locale.ROOT));
        Account saved = accountRepository.save(account);
        return toResponse(saved);
    }

    public List<AccountResponse> listAccounts(String userId) {
        return accountRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public AccountResponse getAccount(@NonNull UUID id, String userId) {
        return toResponse(loadOwnedAccount(id, userId));
    }

    public Account loadOwnedAccount(@NonNull UUID id, String userId) {
        Account account = accountRepository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (!account.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        }
        return account;
    }

    private AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getInstitution(),
                account.getType(),
                account.getCurrency(),
                account.getStatus(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
