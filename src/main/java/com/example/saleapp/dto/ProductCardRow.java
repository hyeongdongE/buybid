package com.example.saleapp.dto;

import com.example.saleapp.entity.StatusEnum;

import java.time.LocalDateTime;

public interface ProductCardRow {
    Long getProductId();
    StatusEnum getStatus();
    Integer getPrice();
    String getTitle();
    LocalDateTime getStartAt();
    LocalDateTime getEndAt();
    String getCategory();
}
