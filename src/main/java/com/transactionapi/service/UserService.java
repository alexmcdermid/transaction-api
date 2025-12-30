package com.transactionapi.service;

import com.transactionapi.model.User;
import com.transactionapi.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void ensureUserExists(String authId, String email) {
        userRepository.findByAuthId(authId).map(existing -> {
            if (email != null && !email.isBlank() && !email.equalsIgnoreCase(existing.getEmail())) {
                existing.setEmail(email);
                return userRepository.save(existing);
            }
            return existing;
        }).orElseGet(() -> {
            User user = new User();
            user.setAuthId(authId);
            if (email != null && !email.isBlank()) {
                user.setEmail(email);
            }
            return userRepository.save(user);
        });
    }
}
