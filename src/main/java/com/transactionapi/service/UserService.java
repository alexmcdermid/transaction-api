package com.transactionapi.service;

import com.transactionapi.constants.PnlDisplayMode;
import com.transactionapi.constants.ThemeMode;
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
        getOrCreateUser(authId, email);
    }

    public User getOrCreateUser(String authId, String email) {
        return userRepository.findByAuthId(authId).map(existing -> {
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

    public User updatePreferences(
            String authId,
            String email,
            ThemeMode themeMode,
            PnlDisplayMode pnlDisplayMode
    ) {
        User user = getOrCreateUser(authId, email);
        if (themeMode != null) {
            user.setThemeMode(themeMode);
        }
        if (pnlDisplayMode != null) {
            user.setPnlDisplayMode(pnlDisplayMode);
        }
        return userRepository.save(user);
    }
}
