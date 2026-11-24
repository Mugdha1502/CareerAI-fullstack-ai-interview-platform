package com.careerai.backend.controller;

import com.careerai.backend.dto.AuthResponse;
import com.careerai.backend.dto.LoginRequest;
import com.careerai.backend.dto.RegisterRequest;
import com.careerai.backend.dto.UserResponse;
import com.careerai.backend.security.ClientIpResolver;
import com.careerai.backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(request, clientIpResolver.resolve(httpRequest)));
    }

    /**
     * Requires a valid JWT (enforced by SecurityConfig). The user is taken from the
     * authenticated security context — no user id is accepted from the client.
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        return ResponseEntity.ok(authService.getCurrentUser(authentication.getName()));
    }
}
