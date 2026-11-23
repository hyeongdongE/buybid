package com.example.saleapp.dto;

import java.util.List;

public record ProductPage(List<ProductCardDTO> content, int page, int size, long totalElements, int totalPages) {}
