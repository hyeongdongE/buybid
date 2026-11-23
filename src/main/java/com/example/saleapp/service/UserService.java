package com.example.saleapp.service;

import com.example.saleapp.component.JwtTokenProvider;
import com.example.saleapp.config.ChatSocketSessions.AccountLogout;
import com.example.saleapp.dto.SignInDTO;
import com.example.saleapp.dto.SignUpDTO;
import com.example.saleapp.entity.RefreshTokenEntity;
import com.example.saleapp.entity.UsersEntity;
import com.example.saleapp.repository.RefreshTokenRepository;
import com.example.saleapp.repository.UsersRepository;
import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.WebUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UsersRepository users;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokens;
    private final RefreshTokenRepository refreshTokens;
    private final ApplicationEventPublisher events;

    @Value("${app.cookie-secure:true}")
    private boolean secureCookies;

    public void signup(SignUpDTO dto) {
        String email = normalizeEmail(dto.getEmail());
        String name = dto.getUsername() == null ? "" : dto.getUsername().strip();
        String password = dto.getPassword();
        if (email.length() > 254 || !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")
                || name.isEmpty() || name.length() > 40 || password == null
                || password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72
                || !password.equals(dto.getConfirmPassword())) {
            throw new IllegalArgumentException("이름, 이메일 및 비밀번호 입력을 확인하세요.");
        }
        if (users.existsByEmail(email)) {
            throw new IllegalArgumentException("이미 등록된 이메일입니다.");
        }
        try {
            users.saveAndFlush(UsersEntity.builder().email(email).password(encoder.encode(password))
                    .name(name).created_at(LocalDateTime.now()).role("ROLE_USER").build());
        } catch (DataIntegrityViolationException exception) {
            if (users.existsByEmail(email)) {
                throw new IllegalArgumentException("이미 등록된 이메일입니다.", exception);
            }
            throw exception;
        }
    }

    public ResponseEntity<Map<String, String>> signin(SignInDTO dto) {
        try {
            String password = dto.getPassword();
            if (password == null || password.getBytes(StandardCharsets.UTF_8).length > 72) {
                throw unauthorized();
            }
            var authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(normalizeEmail(dto.getEmail()), password));
            String email = authentication.getName();
            String refresh = tokens.createRefreshToken(email);
            refreshTokens.save(new RefreshTokenEntity(email, digest(refresh)));
            return sessionResponse(email, refresh);
        } catch (AuthenticationException exception) {
            throw unauthorized();
        }
    }

    public ResponseEntity<Map<String, String>> refresh(HttpServletRequest request) {
        String previous = refreshCookie(request);
        String email = tokens.refreshSubject(previous).orElseThrow(this::unauthorized);
        String replacement = tokens.createRefreshToken(email);
        if (!users.existsByEmail(email) || refreshTokens.rotate(email, digest(previous), digest(replacement)) != 1) {
            throw unauthorized();
        }
        return sessionResponse(email, replacement);
    }

    public ResponseEntity<Void> logout(HttpServletRequest request, String authenticatedEmail) {
        String refresh = refreshCookie(request);
        var revokedEmail = tokens.refreshSubject(refresh)
                .filter(email -> authenticatedEmail == null || authenticatedEmail.equals(email))
                .filter(email -> refreshTokens.revoke(email, digest(refresh)) == 1);
        String email = authenticatedEmail == null ? revokedEmail.orElse(null) : authenticatedEmail;
        if (email != null) {
            events.publishEvent(new AccountLogout(email));
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie("accessToken", "", "/", Duration.ZERO),
                        cookie("refreshToken", "", "/api", Duration.ZERO))
                .build();
    }

    private ResponseEntity<Map<String, String>> sessionResponse(String email, String refresh) {
        String access = tokens.createAccessToken(email);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.SET_COOKIE, cookie("accessToken", access, "/", JwtTokenProvider.ACCESS_TTL),
                        cookie("refreshToken", refresh, "/api", JwtTokenProvider.REFRESH_TTL))
                .body(Map.of("accessToken", access));
    }

    private String cookie(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value).httpOnly(true).secure(secureCookies)
                .path(path).sameSite("Lax").maxAge(maxAge).build().toString();
    }

    private String refreshCookie(HttpServletRequest request) {
        var cookie = WebUtils.getCookie(request, "refreshToken");
        return cookie == null ? null : cookie.getValue();
    }

    private String normalizeEmail(String input) {
        return input == null ? "" : input.strip().toLowerCase(Locale.ROOT);
    }

    private String digest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증 정보를 확인하세요.");
    }
}
