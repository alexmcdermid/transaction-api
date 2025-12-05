package com.transactionapi.controller;

import com.transactionapi.constants.ApiPaths;
import com.transactionapi.dto.CreateTransactionRequest;
import com.transactionapi.dto.TransactionResponse;
import com.transactionapi.security.UserIdResolver;
import com.transactionapi.service.TransactionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Objects;
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
@RequestMapping(ApiPaths.ACCOUNTS + "/{accountId}/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final UserIdResolver userIdResolver;

    public TransactionController(TransactionService transactionService, UserIdResolver userIdResolver) {
        this.transactionService = transactionService;
        this.userIdResolver = userIdResolver;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(
            Authentication authentication,
            @PathVariable UUID accountId,
            @Valid @RequestBody CreateTransactionRequest request
    ) {
        String caller = userIdResolver.requireUserId(authentication);
        TransactionResponse response = transactionService.createTransaction(Objects.requireNonNull(accountId), request, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<TransactionResponse> listTransactions(
            Authentication authentication,
            @PathVariable UUID accountId
    ) {
        String caller = userIdResolver.requireUserId(authentication);
        return transactionService.listTransactions(accountId, caller);
    }

    @DeleteMapping("/{transactionId}")
    public ResponseEntity<Void> deleteTransaction(
            Authentication authentication,
            @PathVariable UUID accountId,
            @PathVariable UUID transactionId
    ) {
        String caller = userIdResolver.requireUserId(authentication);
        transactionService.deleteTransaction(accountId, transactionId, caller);
        return ResponseEntity.noContent().build();
    }
}
