package com.example.saleapp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bid_proxy")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BidProxyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long proxyId;

    @ManyToOne
    @JoinColumn(name = "productId")
    private ProductsEntity productId;

    @ManyToOne
    @JoinColumn(name = "userId")
    private UsersEntity userId;

    private Integer maxPrice;
}
