package com.careerai.backend.config;

import com.careerai.backend.security.CustomUserDetailsService;
import com.careerai.backend.security.JsonAccessDeniedHandler;
import com.careerai.backend.security.JwtAuthenticationEntryPoint;
import com.careerai.backend.security.JwtAuthenticationFilter;
import com.careerai.backend.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    /** CORS_ALLOWED_ORIGINS: comma-separated exact origins; defaults to the Vite dev server. Validated by CorsOrigins. */
    @Value("${careerai.cors.allowed-origins:http://localhost:5173}")
    private List<String> allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        JwtService jwtService,
        CustomUserDetailsService userDetailsService,
        JwtAuthenticationEntryPoint authenticationEntryPoint,
        JsonAccessDeniedHandler accessDeniedHandler
    ) throws Exception {

        // Constructed here (not a @Component) so it only runs inside this chain.
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtService, userDetailsService);

        http
            // Stateless JWT API: no session cookies, so CSRF protection is not needed.
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/error").permitAll()
                // Load balancer / platform health probe: status only, no details.
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                // Public plan catalogue; the Razorpay webhook authenticates by HMAC signature, not JWT.
                .requestMatchers(HttpMethod.GET, "/api/billing/plans").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/billing/razorpay/webhook").permitAll()
                // Every other CareerAI API, including GET /api/auth/me, needs a valid JWT.
                .anyRequest().authenticated()
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(CorsOrigins.parse(allowedOrigins));
        config.setAllowCredentials(false); // JWT travels in the Authorization header, not in cookies
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
