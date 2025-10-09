package com.example.saleapp.dto;

import com.example.saleapp.repository.ProductImageRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ProductDetailDTO {
    private Long id;
    private String title;
    private String userName;
    private String description;
    private Integer currentPrice;
    private Integer startPrice;
    private Integer buyNowPrice;
    private Integer minIncrement;
    private String leaderName;
    private List<ImageViewDTO> url;
}
