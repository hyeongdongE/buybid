package com.example.saleapp.controller;

import com.example.saleapp.config.CustomUserDetail;
import com.example.saleapp.dto.SignInDTO;
import com.example.saleapp.service.UserService;
import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class UserRController {
    private final UserService userService;

    // 로그인
    @PostMapping("/api/signin")
    public ResponseEntity<Map<String, String>> signin(@RequestBody SignInDTO dto) {
        return userService.signin(dto);
    }

    // refreshToken
    @PostMapping("/api/refresh")
    public ResponseEntity<Map<String, String>> refresh(HttpServletRequest request) {
        return userService.refresh(request);
    }

    // 로그아웃
    @PostMapping("/api/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request,
            @AuthenticationPrincipal CustomUserDetail user) {
        return userService.logout(request, user == null ? null : user.getUsername());
    }

    @GetMapping("/api/session")
    public ResponseEntity<Void> session() {
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }
}
