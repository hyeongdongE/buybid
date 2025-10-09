package com.example.saleapp.repository;

import com.example.saleapp.entity.ChatRoomEntity;
import com.example.saleapp.entity.ProductsEntity;
import com.example.saleapp.entity.UsersEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoomEntity, Long> {
    List<ChatRoomEntity> findByBuyerId_UserIdxOrSellarId_UserIdx(Long buyerId, Long sellarId);
    boolean existsByBuyerIdAndSellarIdAndProductId(UsersEntity buyer, UsersEntity seller, ProductsEntity product);

}