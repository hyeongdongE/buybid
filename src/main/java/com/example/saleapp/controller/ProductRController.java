package com.example.saleapp.controller;

import com.example.saleapp.config.CustomUserDetail;
import com.example.saleapp.dto.*;
import com.example.saleapp.repository.ChatRoomRepository;
import com.example.saleapp.repository.ProductsRepository;
import com.example.saleapp.service.ProductService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ProductRController {
    private final ProductService productService;

    // Product_Upload 상품 업로드
    @PostMapping("/api/upload")
    public ResponseEntity<String> upload(@ModelAttribute ProductDTO dto,
                                         @AuthenticationPrincipal CustomUserDetail user) throws Exception {
        return productService.productUpload(dto, user);
    }

    // Product 페이지 상품 리스트 반환
    @GetMapping("/api/products")
    public List<ProductCardDTO> product() {
        return productService.prouductList();
    }

    // Product 입찰 로직
    @PostMapping("/api/products/proxybid/{id}")
    @Transactional
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