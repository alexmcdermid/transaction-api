package com.transactionapi.controller;

import com.transactionapi.constants.ApiPaths;
import com.transactionapi.dto.AccountResponse;
import com.transactionapi.dto.CreateAccountRequest;
import com.transactionapi.security.UserIdResolver;
import com.transactionapi.service.AccountService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.ACCOUNTS)
public class AccountController {

    private final AccountService accountService;
    private final UserIdResolver userIdResolver;

    public AccountController(AccountService accountService, UserIdResolver userIdResolver) {
        this.accountService = accountService;
        this.userIdResolver = userIdResolver;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            Authentication authentication,
            @Valid @RequestBody CreateAccountRequest request
    ) {
        String caller = userIdResolver.requireUserId(authentication);
        AccountResponse response = accountService.createAccount(request, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<AccountResponse> listAccounts(
            Authentication authentication
    ) {
        String caller = userIdResolver.requireUserId(authentication);
        return accountService.listAccounts(caller);
    }

    @GetMapping("/{id}")
    public AccountResponse getAccount(
            Authentication authentication,
            @PathVariable UUID id
    ) {
        String caller = userIdResolver.requireUserId(authentication);
        return accountService.getAccount(Objects.requireNonNull(id), caller);
    }
}
