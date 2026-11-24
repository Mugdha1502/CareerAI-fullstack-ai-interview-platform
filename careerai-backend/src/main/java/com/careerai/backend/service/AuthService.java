package com.careerai.backend.service;

import com.careerai.backend.dto.AuthResponse;
import com.careerai.backend.dto.LoginRequest;
import com.careerai.backend.dto.RegisterRequest;
import com.careerai.backend.dto.UserResponse;
import com.careerai.backend.entity.User;
import com.careerai.backend.exception.EmailAlreadyExistsException;
import com.careerai.backend.exception.InvalidCredentialsException;
import com.careerai.backend.exception.PasswordMismatchException;
import com.careerai.backend.exception.UserNotFoundException;
import com.careerai.backend.repository.UserRepository;
import com.careerai.backend.security.ClientIpResolver.ClientAddress;
import com.careerai.backend.security.JwtService;
import com.careerai.backend.security.LoginRateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginRateLimiter loginRateLimiter;

    /** Hash checked for unknown emails so both cases take the same BCrypt time (no timing hint). */
    private volatile String timingEqualizerHash;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException();
        }

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new PasswordMismatchException();
        }

        User user = User.builder()
            .name(request.getName().trim())
            .email(email)
            .password(passwordEncoder.encode(request.getPassword()))
            .build();

        User saved = userRepository.save(user);

        // New users are signed in straight away.
        return AuthResponse.from(saved, jwtService.generateToken(saved), "Registration successful");
    }

    /**
     * Throttled per account + client IP and per IP (LoginRateLimiter). Unknown emails and wrong
     * passwords get the same 401, take the same BCrypt time and count towards the same limits.
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request, ClientAddress client) {
        String email = normalizeEmail(request.getEmail());
        loginRateLimiter.checkAllowed(email, client);

        User user = userRepository.findByEmail(email).orElse(null);
        boolean valid;
        if (user == null) {
            passwordEncoder.matches(request.getPassword(), timingEqualizerHash()); // result ignored: timing only
            valid = false;
        } else {
            valid = passwordEncoder.matches(request.getPassword(), user.getPassword());
        }

        if (!valid) {
            loginRateLimiter.recordFailure(email, client);
            throw new InvalidCredentialsException();
        }

        loginRateLimiter.recordSuccess(email, client);
        return AuthResponse.from(user, jwtService.generateToken(user), "Login successful");
    }

    private String timingEqualizerHash() {
        String hash = timingEqualizerHash;
        if (hash == null) {
            hash = passwordEncoder.encode("careerai-timing-equalizer-" + UUID.randomUUID());
            timingEqualizerHash = hash;
        }
        return hash;
    }

    /**
     * Returns the user identified by the authenticated JWT subject (email).
     * The id never comes from the client.
     */
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmail(normalizeEmail(email))
            .orElseThrow(UserNotFoundException::new);

        return UserResponse.from(user);
    }

    /** Emails are stored lower-case so "Mugdha@x.com" and "mugdha@x.com" are one account. */
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
