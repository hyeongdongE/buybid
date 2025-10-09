package com.example.saleapp.repository;

import com.example.saleapp.entity.BidDetailsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BidDetailsRepository extends JpaRepository<BidDetailsEntity, Long> {
}
