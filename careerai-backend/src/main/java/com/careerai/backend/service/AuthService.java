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
import com.careerai.backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

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

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.getEmail()))
            .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        return AuthResponse.from(user, jwtService.generateToken(user), "Login successful");
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
