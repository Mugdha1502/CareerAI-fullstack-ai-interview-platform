package com.careerai.backend.security;

import com.careerai.backend.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Issues and verifies HS256-signed JWTs.
 *
 * Subject = the user's email. Extra claims are limited to userId, role and plan —
 * never passwords or profile details. The signing secret comes from configuration
 * (app.jwt.secret / JWT_SECRET), not from source code.
 */
@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32; // HS256 requires a 256-bit key
    private static final int MIN_DISTINCT_CHARACTERS = 10;
    private static final String ISSUER = "careerai";

    /** Values that are public (old defaults, documentation placeholders) and must never sign tokens. */
    private static final Set<String> PUBLIC_SECRETS = Set.of(
        "development-only-secret-that-must-be-replaced",
        "replace-with-a-long-random-secret-of-32-plus-characters",
        "replace-with-your-own-long-random-local-secret",
        "your-jwt-secret",
        "changeme",
        "secret"
    );

    private final SecretKey signingKey;
    private final long expirationMillis;

    public JwtService(
        @Value("${app.jwt.secret}") String secret,
        @Value("${app.jwt.expiration}") long expirationMillis
    ) {
        byte[] keyBytes = requireStrongSecret(secret).getBytes(StandardCharsets.UTF_8);

        if (expirationMillis <= 0) {
            throw new IllegalStateException("app.jwt.expiration must be a positive number of milliseconds.");
        }

        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMillis = expirationMillis;
    }

    /**
     * There is no built-in fallback secret: a deployment must set JWT_SECRET, and local development
     * keeps its own value in the git-ignored application-local.properties (careerai.local.jwt-secret).
     */
    static String requireStrongSecret(String secret) {
        String value = secret == null ? "" : secret.trim();
        if (value.isEmpty()) {
            throw new IllegalStateException("JWT signing secret is not set. Set the JWT_SECRET environment variable "
                + "(locally you can use careerai.local.jwt-secret in application-local.properties).");
        }
        if (value.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes for HS256.");
        }
        if (PUBLIC_SECRETS.contains(value.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("JWT_SECRET is a publicly known placeholder. Generate a new random secret.");
        }
        if (value.chars().distinct().count() < MIN_DISTINCT_CHARACTERS) {
            throw new IllegalStateException("JWT_SECRET is too predictable. Use a long random value.");
        }
        return value;
    }

    public String generateToken(User user) {
        Date now = new Date();

        return Jwts.builder()
            .issuer(ISSUER)
            .subject(user.getEmail())
            .claims(Map.of(
                "userId", user.getId(),
                "role", user.getRole().name(),
                "plan", user.getPlan().name()
            ))
            .issuedAt(now)
            .expiration(new Date(now.getTime() + expirationMillis))
            .signWith(signingKey, Jwts.SIG.HS256)
            .compact();
    }

    /**
     * Verifies the signature and expiry and returns the claims.
     * Throws ExpiredJwtException, or another JwtException / IllegalArgumentException
     * for malformed or tampered tokens — callers decide how to report each.
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(signingKey)
            .requireIssuer(ISSUER)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(parseClaims(token));
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public boolean isTokenExpired(String token) {
        try {
            return extractClaim(token, Claims::getExpiration).before(new Date());
        } catch (ExpiredJwtException ex) {
            // The parser rejects expired tokens before returning claims.
            return true;
        }
    }

    /** True when the token is correctly signed, unexpired and belongs to this user. */
    public boolean validateToken(String token, UserDetails userDetails) {
        String username = extractUsername(token);
        return username != null
            && username.equalsIgnoreCase(userDetails.getUsername())
            && !isTokenExpired(token);
    }

    public long getExpirationMillis() {
        return expirationMillis;
    }
}
