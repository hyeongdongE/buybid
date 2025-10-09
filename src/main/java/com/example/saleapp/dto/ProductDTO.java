package com.example.saleapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDTO {
    private String title;
    private String description;
    private String category;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer startPrice;
    private Integer minIncrement;
    private Integer buyNow;
    private List<MultipartFile> images;
    private List<Integer> orders;
}
