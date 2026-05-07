package com.transactionapi.controller;

import com.transactionapi.constants.ApiPaths;
import com.transactionapi.dto.TradeHistoryResponse;
import com.transactionapi.dto.UserResponse;
import com.transactionapi.repository.UserRepository;
import com.transactionapi.security.UserIdResolver;
import com.transactionapi.service.TradeService;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(ApiPaths.ADMIN_USERS)
public class AdminUserController {

    private final UserRepository userRepository;
    private final UserIdResolver userIdResolver;
    private final TradeService tradeService;

    public AdminUserController(
            UserRepository userRepository,
            UserIdResolver userIdResolver,
            TradeService tradeService
    ) {
        this.userRepository = userRepository;
        this.userIdResolver = userIdResolver;
        this.tradeService = tradeService;
    }

    @GetMapping
    public List<UserResponse> listUsers(Authentication authentication) {
        userIdResolver.requireAdmin(authentication);
        return userRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(UserResponse::from)
                .toList();
    }

    @GetMapping("/{userId}/trade-history")
    public List<TradeHistoryResponse> listUserTradeHistory(
            Authentication authentication,
            @PathVariable UUID userId
    ) {
        userIdResolver.requireAdmin(authentication);
        String authId = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"))
                .getAuthId();
        return tradeService.listTradeHistoryForUser(authId);
    }
}
