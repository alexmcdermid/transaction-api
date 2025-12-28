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

    public void ensureUserExists(String authId) {
        userRepository.findByAuthId(authId).orElseGet(() -> {
            User user = new User();
            user.setAuthId(authId);
            return userRepository.save(user);
        });
    }
}
