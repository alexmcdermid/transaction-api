package com.transactionapi.controller;

import com.transactionapi.constants.ApiPaths;
import com.transactionapi.dto.PnlSummaryResponse;
import com.transactionapi.dto.TradeRequest;
import com.transactionapi.dto.TradeResponse;
import com.transactionapi.security.UserIdResolver;
import com.transactionapi.service.TradeService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.TRADES)
public class TradeController {

    private final TradeService tradeService;
    private final UserIdResolver userIdResolver;

    public TradeController(TradeService tradeService, UserIdResolver userIdResolver) {
        this.tradeService = tradeService;
        this.userIdResolver = userIdResolver;
    }

    @GetMapping
    public List<TradeResponse> list(Authentication authentication) {
        String userId = userIdResolver.requireUserId(authentication);
        return tradeService.listTrades(userId);
    }

    @PostMapping
    public ResponseEntity<TradeResponse> create(
            Authentication authentication,
            @Valid @RequestBody TradeRequest request
    ) {
        String userId = userIdResolver.requireUserId(authentication);
        TradeResponse response = tradeService.createTrade(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{tradeId}")
    public TradeResponse update(
            Authentication authentication,
            @PathVariable UUID tradeId,
            @Valid @RequestBody TradeRequest request
    ) {
        String userId = userIdResolver.requireUserId(authentication);
        return tradeService.updateTrade(tradeId, request, userId);
    }

    @DeleteMapping("/{tradeId}")
    public ResponseEntity<Void> delete(
            Authentication authentication,
            @PathVariable UUID tradeId
    ) {
        String userId = userIdResolver.requireUserId(authentication);
        tradeService.deleteTrade(tradeId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/summary")
    public PnlSummaryResponse summary(Authentication authentication) {
        String userId = userIdResolver.requireUserId(authentication);
        return tradeService.summarize(userId);
    }
}
