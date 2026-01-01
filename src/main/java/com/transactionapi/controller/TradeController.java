package com.transactionapi.controller;

import com.transactionapi.constants.ApiPaths;
import com.transactionapi.dto.AggregateStatsResponse;
import com.transactionapi.dto.PagedResponse;
import com.transactionapi.dto.PnlSummaryResponse;
import com.transactionapi.dto.TradeRequest;
import com.transactionapi.dto.TradeResponse;
import com.transactionapi.security.UserIdResolver;
import com.transactionapi.service.UserService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.TRADES)
public class TradeController {

    private final TradeService tradeService;
    private final UserIdResolver userIdResolver;
    private final UserService userService;

    public TradeController(TradeService tradeService, UserIdResolver userIdResolver, UserService userService) {
        this.tradeService = tradeService;
        this.userIdResolver = userIdResolver;
        this.userService = userService;
    }

    @GetMapping
    public List<TradeResponse> list(
            Authentication authentication,
            @RequestParam(required = false) String month
    ) {
        String userId = userIdResolver.requireUserId(authentication);
        userService.ensureUserExists(userId, userIdResolver.resolveEmail(authentication));
        return tradeService.listTrades(userId, 0, 100, parseMonth(month)).items();
    }

    @PostMapping
    public ResponseEntity<TradeResponse> create(
            Authentication authentication,
            @Valid @RequestBody TradeRequest request
    ) {
        String userId = userIdResolver.requireUserId(authentication);
        userService.ensureUserExists(userId, userIdResolver.resolveEmail(authentication));
        TradeResponse response = tradeService.createTrade(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/paged")
    public PagedResponse<TradeResponse> listPaged(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String month
    ) {
        String userId = userIdResolver.requireUserId(authentication);
        userService.ensureUserExists(userId, userIdResolver.resolveEmail(authentication));
        return tradeService.listTrades(userId, page, size, parseMonth(month));
    }

    @PutMapping("/{tradeId}")
    public TradeResponse update(
            Authentication authentication,
            @PathVariable UUID tradeId,
            @Valid @RequestBody TradeRequest request
    ) {
        String userId = userIdResolver.requireUserId(authentication);
        userService.ensureUserExists(userId, userIdResolver.resolveEmail(authentication));
        return tradeService.updateTrade(tradeId, request, userId);
    }

    @DeleteMapping("/{tradeId}")
    public ResponseEntity<Void> delete(
            Authentication authentication,
            @PathVariable UUID tradeId
    ) {
        String userId = userIdResolver.requireUserId(authentication);
        userService.ensureUserExists(userId, userIdResolver.resolveEmail(authentication));
        tradeService.deleteTrade(tradeId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/summary")
    public PnlSummaryResponse summary(
            Authentication authentication,
            @RequestParam(required = false) String month
    ) {
        String userId = userIdResolver.requireUserId(authentication);
        userService.ensureUserExists(userId, userIdResolver.resolveEmail(authentication));
        return tradeService.summarize(userId, parseMonth(month));
    }

    @GetMapping("/stats")
    public AggregateStatsResponse stats(Authentication authentication) {
        String userId = userIdResolver.requireUserId(authentication);
        userService.ensureUserExists(userId, userIdResolver.resolveEmail(authentication));
        return tradeService.getAggregateStats(userId);
    }

    private static java.time.YearMonth parseMonth(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            // Accept YYYY-MM or YYYY-MM-DD
            String trimmed = value.trim();
            if (trimmed.length() == 7) {
                return java.time.YearMonth.parse(trimmed);
            }
            return java.time.YearMonth.from(java.time.LocalDate.parse(trimmed));
        } catch (Exception e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid month format, expected YYYY-MM"
            );
        }
    }
}
