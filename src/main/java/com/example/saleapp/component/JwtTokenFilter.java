package com.example.saleapp.component;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtTokenFilter extends OncePerRequestFilter {
    private final JwtTokenProvider tokens;
    private final UserDetailsService users;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            tokens.accessSession(accessToken(request)).ifPresent(session -> {
                try {
                    var user = users.loadUserByUsername(session.email());
                    var authentication = UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
                    authentication.setDetails(session.expiresAt());
                    var context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(authentication);
                    SecurityContextHolder.setContext(context);
                } catch (UsernameNotFoundException ignored) {
                    // A token for a deleted account does not authenticate the request.
                }
            });
        }
        chain.doFilter(request, response);
    }

    private String accessToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null) {
            return header.startsWith("Bearer ") ? header.substring(7) : null;
        }
        Cookie cookie = WebUtils.getCookie(request, "accessToken");
        return cookie == null ? null : cookie.getValue();
    }
}
