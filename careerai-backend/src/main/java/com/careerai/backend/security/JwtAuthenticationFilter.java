package com.careerai.backend.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates requests that carry "Authorization: Bearer <jwt>".
 *
 * The filter never rejects a request itself: on a missing, malformed, expired or
 * tampered token it records the reason and continues unauthenticated. Public
 * endpoints still work, and protected ones are answered with a 401 by
 * {@link JwtAuthenticationEntryPoint}, which reads the recorded reason.
 *
 * Created in SecurityConfig (not a @Component) so it runs only inside the
 * security filter chain, not a second time as a plain servlet filter.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_ERROR_ATTRIBUTE = "careerai.auth.error";

    public enum AuthError {
        INVALID_SCHEME,
        EXPIRED,
        INVALID,
        USER_NOT_FOUND
    }

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        // 1. No Authorization header: continue unauthenticated.
        if (header == null || header.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Only the Bearer scheme is accepted.
        if (!header.startsWith(BEARER_PREFIX)) {
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, AuthError.INVALID_SCHEME);
            filterChain.doFilter(request, response);
            return;
        }

        // 3. Extract the token.
        String token = header.substring(BEARER_PREFIX.length()).trim();

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                // 4. Validate signature, issuer and expiry, then 5. load the user.
                String email = jwtService.extractUsername(token);
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                if (jwtService.validateToken(token, userDetails)) {
                    // 6. Populate the SecurityContext.
                    UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContext context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(authentication);
                    SecurityContextHolder.setContext(context);
                } else {
                    request.setAttribute(AUTH_ERROR_ATTRIBUTE, AuthError.INVALID);
                }
            } catch (ExpiredJwtException ex) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, AuthError.EXPIRED);
            } catch (UsernameNotFoundException ex) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, AuthError.USER_NOT_FOUND);
            } catch (JwtException | IllegalArgumentException ex) {
                // Malformed, wrongly signed, wrong issuer, or empty token.
                log.debug("Rejected JWT: {}", ex.getMessage());
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, AuthError.INVALID);
            }
        }

        // 7. Continue the chain.
        filterChain.doFilter(request, response);
    }
}
