package com.careerai.backend.service;

import com.careerai.backend.entity.User;
import com.careerai.backend.exception.UserNotFoundException;
import com.careerai.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Resolves the authenticated user from the JWT subject (email).
 * Controllers pass Authentication#getName(); no endpoint accepts a user id.
 */
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public User require(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
            .orElseThrow(UserNotFoundException::new);
    }
}
