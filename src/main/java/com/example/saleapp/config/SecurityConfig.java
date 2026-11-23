package com.example.saleapp.config;

import com.example.saleapp.component.JwtTokenFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtTokenFilter jwtTokenFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Value("${app.cookie-secure:true}") boolean secure) throws Exception {
        var csrfTokens = new CookieCsrfTokenRepository();
        csrfTokens.setCookieCustomizer(cookie -> cookie.secure(secure).httpOnly(true).sameSite("Lax"));
        return http
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens).ignoringRequestMatchers("/ws/**"))
                // JWT authenticates every request. An implicit session strategy would clear the CSRF cookie on each request.
                .sessionManagement(AbstractHttpConfigurer::disable)
                .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, failure) -> {
                    String path = request.getRequestURI().substring(request.getContextPath().length());
                    if (path.startsWith("/api/") || path.startsWith("/chat/history/") || path.startsWith("/ws/")) {
                        response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    } else {
                        response.sendRedirect(request.getContextPath() + "/signin?next="
                                + URLEncoder.encode(path, StandardCharsets.UTF_8));
                    }
                }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/main", "/signin", "/signup", "/about", "/productdetail",
                                "/api/products", "/css/**", "/js/**", "/image/**", "/files/products/**", "/favicon.ico").permitAll()
                        .requestMatchers(HttpMethod.POST, "/signup", "/api/signin", "/api/refresh", "/api/logout").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtTokenFilter, CsrfFilter.class)
                .build();
    }

    @Bean
    FilterRegistrationBean<JwtTokenFilter> jwtServletRegistration() {
        var registration = new FilterRegistrationBean<>(jwtTokenFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
