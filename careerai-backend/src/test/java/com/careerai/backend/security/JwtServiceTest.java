package com.careerai.backend.security;

import com.careerai.backend.entity.Plan;
import com.careerai.backend.entity.Role;
import com.careerai.backend.entity.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** JWT signing secret rules and token validation (no Spring context). */
class JwtServiceTest {

    private static final String STRONG = "kq3Zt8pW1vXy6NrB0cLmH5sQa9DfGjE2uYiO7TwRzKx";

    private static User user() {
        User user = new User();
        user.setId(42L);
        user.setEmail("jwt@example.test");
        user.setRole(Role.USER);
        user.setPlan(Plan.FREE);
        return user;
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "   ",
        "too-short-secret",
        "development-only-secret-that-must-be-replaced",
        "replace-with-a-long-random-secret-of-32-plus-characters",
        "replace-with-your-own-long-random-local-secret",
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "abababababababababababababababababababab"
    })
    void weakMissingOrPublicSecretsStopStartup(String secret) {
        assertThatThrownBy(() -> new JwtService(secret, 3_600_000)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nullSecretStopsStartupWithAHelpfulMessage() {
        assertThatThrownBy(() -> new JwtService(null, 3_600_000))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void validTokenRoundTrips() {
        JwtService service = new JwtService(STRONG, 3_600_000);
        String token = service.generateToken(user());
        assertThat(service.extractUsername(token)).isEqualTo("jwt@example.test");
        assertThat(service.parseClaims(token).get("userId", Long.class)).isEqualTo(42L);
        assertThat(service.isTokenExpired(token)).isFalse();
    }

    @Test
    void tokensFromAnotherSecretOrTamperedTokensAreRejected() {
        JwtService service = new JwtService(STRONG, 3_600_000);
        JwtService other = new JwtService("Zx9" + STRONG.substring(3), 3_600_000);
        String foreign = other.generateToken(user());
        assertThatThrownBy(() -> service.parseClaims(foreign)).isInstanceOf(JwtException.class);

        String token = service.generateToken(user());
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1].substring(0, parts[1].length() - 2) + "AA." + parts[2];
        assertThatThrownBy(() -> service.parseClaims(tampered)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> service.parseClaims("not.a.jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokensAndWrongIssuerAreRejected() {
        JwtService service = new JwtService(STRONG, 3_600_000);
        var key = Keys.hmacShaKeyFor(STRONG.getBytes(StandardCharsets.UTF_8));
        String expired = Jwts.builder().issuer("careerai").subject("jwt@example.test")
            .issuedAt(new Date(System.currentTimeMillis() - 7_200_000))
            .expiration(new Date(System.currentTimeMillis() - 3_600_000))
            .signWith(key, Jwts.SIG.HS256).compact();
        assertThatThrownBy(() -> service.parseClaims(expired)).isInstanceOf(ExpiredJwtException.class);
        assertThat(service.isTokenExpired(expired)).isTrue();

        String wrongIssuer = Jwts.builder().issuer("someone-else").subject("jwt@example.test")
            .expiration(new Date(System.currentTimeMillis() + 3_600_000))
            .signWith(key, Jwts.SIG.HS256).compact();
        assertThatThrownBy(() -> service.parseClaims(wrongIssuer)).isInstanceOf(JwtException.class);
    }

    @Test
    void unsignedTokensAreRejected() {
        JwtService service = new JwtService(STRONG, 3_600_000);
        String unsigned = Jwts.builder().issuer("careerai").subject("jwt@example.test")
            .expiration(new Date(System.currentTimeMillis() + 3_600_000)).compact();
        assertThatThrownBy(() -> service.parseClaims(unsigned)).isInstanceOf(JwtException.class);
    }
}
