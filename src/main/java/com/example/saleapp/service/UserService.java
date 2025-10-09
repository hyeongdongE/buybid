package com.example.saleapp.service;

import com.example.saleapp.component.JwtTokenProvider;
import com.example.saleapp.dto.SignInDTO;
import com.example.saleapp.dto.SignUpDTO;
import com.example.saleapp.entity.UsersEntity;
import com.example.saleapp.repository.UsersRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private final UsersRepository usersRepository;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenAbapter refreshTokenAbapter;

    public void signup(SignUpDTO dto) {
        if (usersRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다.");
        }

        UsersEntity user = UsersEntity.builder()
                .email(dto.getEmail())
                .password(encoder.encode(dto.getPassword()))
                .name(dto.getUsername())
                .created_at(LocalDateTime.now())
                .role("ROLE_USER")
                .build();

        usersRepository.save(user);
    }

    public ResponseEntity<Map<String, String>> signin(SignInDTO dto, HttpServletResponse response) {

        UsersEntity user = usersRepository.findByEmail(dto.getEmail()).orElseThrow();

        Authentication authentication = authenticationManager
                .authenticate(new UsernamePasswordAuthenticationToken(dto.getEmail(), dto.getPassword()));

        String accessToken = jwtTokenProvider.createAccessToken(
                authentication.getName(),
                authentication.getAuthorities());

        String refreshToken = jwtTokenProvider.createRefreshToken(authentication.getName());

        ResponseCookie responseCookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(false)
                .path("/")
                .sameSite("Lax")
                .maxAge(1000L * 60 * 60 * 24 * 7)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());
        refreshTokenAbapter.save(dto.getEmail(), refreshToken);

        return ResponseEntity.ok(Map.of("accessToken", accessToken));
    }

    public ResponseEntity<Map<String, String>> refresh(HttpServletRequest request) {
        String refreshToken = extractFromCookie(request);

        if (jwtTokenProvider.validateToken(refreshToken)) {
            String email = jwtTokenProvider.getEmail(refreshToken);
            String saved = refreshTokenAbapter.find(email);

            if (!saved.equals(refreshToken)) {
                throw new RuntimeException("Refresh Token 불일치");
            }

            String newAccessToken = jwtTokenProvider.createAccessToken(email, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            String newRefreshToken = jwtTokenProvider.createRefreshToken(email);

            // RT 갱신 (토큰 로테이션)
            refreshTokenAbapter.save(email, newRefreshToken);

            ResponseCookie responseCookie = ResponseCookie.from("refreshToken", newRefreshToken)
                    .httpOnly(true)
                    .secure(false)
                    .path("/")
                    .sameSite("Lax")
                    .maxAge(1000L * 60 * 60 * 24 * 7)
                    .build();

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, responseCookie.toString())
                    .body(Map.of("accessToken", newAccessToken));
        }
        return ResponseEntity.ok(Map.of());
    }

    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractFromCookie(request);

        if (refreshToken != null && jwtTokenProvider.validateToken(refreshToken)) {
            refreshTokenAbapter.delete(jwtTokenProvider.getEmail(refreshToken));
        }

        ResponseCookie deleteCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .sameSite("Lax")
                .maxAge(0)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, deleteCookie.toString());

        return ResponseEntity.ok().build();
    }

    public String extractFromCookie(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("refreshToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}