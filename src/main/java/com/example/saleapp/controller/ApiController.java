package com.example.saleapp.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ApiController {

    @GetMapping("/main")
    public String main() {
        return "index";
    }

    @GetMapping("/about")
    public String about() {
        return "about";
    }
    @GetMapping("/productupload")
    public String productupload() {
        return "product_upload";
    }

    @GetMapping("/signin")
    public String singin() {
        return "signIn";
    }
}
