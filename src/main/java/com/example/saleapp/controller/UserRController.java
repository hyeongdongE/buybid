package com.example.saleapp.controller;

import com.example.saleapp.dto.SignInDTO;
import com.example.saleapp.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class UserRController {
    private final UserService userService;

    // 로그인
    @PostMapping("/api/signin")
    public ResponseEntity<Map<String, String>> signin(@RequestBody SignInDTO dto, HttpServletResponse response) {
        return userService.signin(dto, response);
    }

    // refreshToken
    @PostMapping("/api/refresh")
    public ResponseEntity<Map<String, String>> refresh(HttpServletRequest request) {
        return userService.refresh(request);
    }

    // 로그아웃
    @PostMapping("/api/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        return userService.logout(request, response);
    }
}
