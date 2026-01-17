package com.transactionapi.controller;

import com.transactionapi.constants.ApiPaths;
import com.transactionapi.dto.UserPreferencesRequest;
import com.transactionapi.dto.UserPreferencesResponse;
import com.transactionapi.security.UserIdResolver;
import com.transactionapi.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(ApiPaths.USER_PREFERENCES)
public class UserPreferencesController {

    private final UserIdResolver userIdResolver;
    private final UserService userService;

    public UserPreferencesController(UserIdResolver userIdResolver, UserService userService) {
        this.userIdResolver = userIdResolver;
        this.userService = userService;
    }

    @GetMapping
    public UserPreferencesResponse getPreferences(Authentication authentication) {
        String userId = userIdResolver.requireUserId(authentication);
        String email = userIdResolver.resolveEmail(authentication);
        return UserPreferencesResponse.from(userService.getOrCreateUser(userId, email));
    }

    @PutMapping
    public UserPreferencesResponse updatePreferences(
            @RequestBody UserPreferencesRequest request,
            Authentication authentication
    ) {
        if (request == null || (request.themeMode() == null && request.pnlDisplayMode() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one preference is required");
        }
        String userId = userIdResolver.requireUserId(authentication);
        String email = userIdResolver.resolveEmail(authentication);
        return UserPreferencesResponse.from(
                userService.updatePreferences(
                        userId,
                        email,
                        request.themeMode(),
                        request.pnlDisplayMode()
                )
        );
    }
}
