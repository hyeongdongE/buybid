package com.example.saleapp.controller;

import com.example.saleapp.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;


@Controller
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

    // Product_Detail 페이지
    @GetMapping("/productdetail")
    public String productDetail(@RequestParam("id") Long productId, Model model) {
        return productService.productdetail(productId, model);
    }
}
