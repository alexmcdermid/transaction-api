package com.transactionapi.controller;

import com.transactionapi.constants.ApiPaths;
import com.transactionapi.dto.UserProfileResponse;
import com.transactionapi.model.User;
import com.transactionapi.security.UserIdResolver;
import com.transactionapi.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

    // Public so a signed-out page load gets 204 instead of a 401 the browser logs as an error.
    @GetMapping
    public ResponseEntity<UserProfileResponse> getProfile(Authentication authentication) {
        if (
                authentication == null
                        || !authentication.isAuthenticated()
                        || authentication instanceof AnonymousAuthenticationToken
        ) {
            return ResponseEntity.noContent().build();
        }
        String userId = userIdResolver.requireUserId(authentication);
        String email = userIdResolver.resolveEmail(authentication);
        User user = userService.getOrCreateUser(userId, email);
        return ResponseEntity.ok(UserProfileResponse.from(user, userIdResolver.isAdmin(authentication)));
    }

    @PostMapping("/legal-agreement")
    public UserProfileResponse acceptLegalAgreement(Authentication authentication) {
        String userId = userIdResolver.requireUserId(authentication);
        String email = userIdResolver.resolveEmail(authentication);
        User user = userService.acceptLegalAgreement(userId, email);
        return UserProfileResponse.from(user, userIdResolver.isAdmin(authentication));
    }
}
