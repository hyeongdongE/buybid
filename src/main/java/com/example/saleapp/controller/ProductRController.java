package com.example.saleapp.controller;

import com.example.saleapp.config.CustomUserDetail;
import com.example.saleapp.dto.*;
import com.example.saleapp.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@Slf4j
public class ProductRController {
    private final ProductService productService;

    // Product_Upload 상품 업로드
    @PostMapping("/api/upload")
    public ResponseEntity<String> upload(@ModelAttribute ProductDTO dto,
                                         @AuthenticationPrincipal CustomUserDetail user) {
        return productService.productUpload(dto, user);
    }

    // Product 페이지 상품 리스트 반환
    @GetMapping("/api/products")
    public ProductPage product(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "recent") String sort) {
        return productService.productList(page, size, category, sort);
    }

    // Product 입찰 로직
    @PostMapping("/api/products/proxybid/{id}")
    public BidDTO proxyBid(@PathVariable("id") Long productId,
                           @RequestBody BidRequestDTO dto,
                           @AuthenticationPrincipal CustomUserDetail user) {
        return productService.bidProxy(productId, dto, user);
    }

    // 즉시 구매 로직
    @PostMapping("/api/products/buynow/{id}")
    public BuyNowDTO buyNow(@PathVariable("id") Long productId,
                                         @AuthenticationPrincipal CustomUserDetail user) {
        return productService.buynow(productId, user);
    }
}
