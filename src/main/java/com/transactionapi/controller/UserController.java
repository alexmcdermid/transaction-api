package com.transactionapi.controller;

import com.transactionapi.constants.ApiPaths;
import com.transactionapi.dto.UserProfileResponse;
import com.transactionapi.security.UserIdResolver;
import com.transactionapi.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.USER_ME)
public class UserController {

    private final UserIdResolver userIdResolver;
    private final UserService userService;

    public UserController(UserIdResolver userIdResolver, UserService userService) {
        this.userIdResolver = userIdResolver;
        this.userService = userService;
    }

    @GetMapping
    public UserProfileResponse getProfile(Authentication authentication) {
        String userId = userIdResolver.requireUserId(authentication);
        String email = userIdResolver.resolveEmail(authentication);
        return UserProfileResponse.from(userService.getOrCreateUser(userId, email));
    }
}
