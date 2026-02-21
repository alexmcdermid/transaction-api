package com.transactionapi.controller;

import com.transactionapi.constants.ApiPaths;
import com.transactionapi.dto.AccountRequest;
import com.transactionapi.dto.AccountResponse;
import com.transactionapi.security.UserIdResolver;
import com.transactionapi.service.AccountService;
import com.transactionapi.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.USER_ACCOUNTS)
public class AccountController {

    private final AccountService accountService;
    private final UserIdResolver userIdResolver;
    private final UserService userService;

    public AccountController(AccountService accountService, UserIdResolver userIdResolver, UserService userService) {
        this.accountService = accountService;
        this.userIdResolver = userIdResolver;
        this.userService = userService;
    }

    @GetMapping
    public List<AccountResponse> listAccounts(Authentication authentication) {
        String userId = userIdResolver.requireUserId(authentication);
        userService.ensureUserExists(userId, userIdResolver.resolveEmail(authentication));
        return accountService.listAccounts(userId).stream().map(AccountResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            Authentication authentication,
            @Valid @RequestBody AccountRequest request
    ) {
        String userId = userIdResolver.requireUserId(authentication);
        userService.ensureUserExists(userId, userIdResolver.resolveEmail(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(
                AccountResponse.from(accountService.createAccount(userId, request))
        );
    }

    @DeleteMapping("/{accountId}")
    public ResponseEntity<Void> deleteAccount(
            Authentication authentication,
            @PathVariable UUID accountId
    ) {
        String userId = userIdResolver.requireUserId(authentication);
        userService.ensureUserExists(userId, userIdResolver.resolveEmail(authentication));
        accountService.deleteAccount(userId, accountId);
        return ResponseEntity.noContent().build();
    }
}
