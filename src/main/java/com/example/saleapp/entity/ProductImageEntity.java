package com.example.saleapp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_image", uniqueConstraints = @UniqueConstraint(name = "uk_product_image_sort", columnNames = {"product_id", "sort"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductImageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "image_idx")
    private Long imageIdx;

    private String url;
    private int sort;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private ProductsEntity productId;
}
