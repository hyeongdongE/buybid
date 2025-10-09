package com.example.saleapp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long productId;

    private String title;
    private String content;

    @Column(name = "start_at")
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    @Column(name = "start_price")
    private Integer startPrice;

    @Column(name = "min_increment")
    private Integer minIncrement;

    @Column(name = "buy_now")
    private Integer buyNow;

    @Column(name = "current_price")
    private Integer currentPrice;

    @Enumerated(EnumType.STRING)
    private StatusEnum status;

    @ManyToOne
    @JoinColumn(name = "seller_idx")
    private UsersEntity sellerIdx;

    @ManyToOne
    @JoinColumn(name = "winner_idx")
    private UsersEntity winnerIdx;

    @ManyToOne
    @JoinColumn(name = "cate_id")
    private CategoriesEntity cateId;

    @ManyToOne
    @JoinColumn(name = "leader_idx")
    private UsersEntity leaderIdx;
}
