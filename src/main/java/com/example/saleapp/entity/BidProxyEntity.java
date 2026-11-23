package com.example.saleapp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bid_proxy", uniqueConstraints = @UniqueConstraint(name = "uk_bid_proxy_product_user", columnNames = {"product_id", "user_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BidProxyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long proxyId;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private ProductsEntity productId;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private UsersEntity userId;

    private Integer maxPrice;

    @Column(name = "rank_order", nullable = false)
    private Long rankOrder;
}
