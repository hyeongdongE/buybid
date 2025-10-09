package com.example.saleapp.repository;

import com.example.saleapp.entity.ProductsEntity;
import com.example.saleapp.entity.StatusEnum;
import com.example.saleapp.entity.UsersEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductsRepository extends JpaRepository<ProductsEntity, Long> {
    List<ProductsEntity> findByStatusAndStartAtBefore(StatusEnum status, LocalDateTime dateTime);
    List<ProductsEntity> findByStatusAndEndAtBefore(StatusEnum status, LocalDateTime dateTime);
    Optional<ProductsEntity> findBySellerIdx(UsersEntity sellar);
}