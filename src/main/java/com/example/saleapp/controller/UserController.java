package com.example.saleapp.controller;

import com.example.saleapp.dto.SignUpDTO;
import com.example.saleapp.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
@Slf4j
public class UserController {
    private final UserService userService;

    // 회원가입
    @GetMapping("/signup")
    public String signup(Model model) {
        model.addAttribute("signUpDTO", new SignUpDTO());
        return "signUp";
    }

    @PostMapping("/signup")
    public String signup(@ModelAttribute("signUpDTO") SignUpDTO dto, Model model) {
        if (!(dto.getPassword().equals(dto.getConfirmPassword()))) {
            model.addAttribute("msg", "비밀번호가 일치하지않습니다.");
            model.addAttribute("redirectURL", "/signup");
            return "message";
        }

        userService.signup(dto);
        model.addAttribute("msg", "회원가입 성공하셨습니다!");
        model.addAttribute("redirectURL", "/signin");
        return "message";
    }
}
