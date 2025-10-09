package com.example.saleapp.repository;

import com.example.saleapp.entity.BidProxyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BidProxyRepository extends JpaRepository<BidProxyEntity, Long> {
    // 상품 id와 유저 id로 찾기
    Optional<BidProxyEntity> findByProductId_ProductIdAndUserId_UserIdx(Long productId, Long userId);

    // 상품 id로 값이 가장 큰 엔티티 찾기
    Optional<BidProxyEntity> findFirstByProductId_ProductIdOrderByMaxPriceDesc(Long productId);
}