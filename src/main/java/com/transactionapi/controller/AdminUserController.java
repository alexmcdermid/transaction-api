package com.transactionapi.controller;

import com.transactionapi.constants.ApiPaths;
import com.transactionapi.dto.UserResponse;
import com.transactionapi.repository.UserRepository;
import com.transactionapi.security.UserIdResolver;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.ADMIN_USERS)
public class AdminUserController {

    private final UserRepository userRepository;
    private final UserIdResolver userIdResolver;

    public AdminUserController(UserRepository userRepository, UserIdResolver userIdResolver) {
        this.userRepository = userRepository;
        this.userIdResolver = userIdResolver;
    }

    @GetMapping
    public List<UserResponse> listUsers(Authentication authentication) {
        userIdResolver.requireAdmin(authentication);
        return userRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(UserResponse::from)
                .toList();
    }
}
