package com.example.saleapp.repository;

import com.example.saleapp.entity.CategoriesEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoriesRepository extends JpaRepository<CategoriesEntity, Integer> {
    Optional<CategoriesEntity> findByName(String name);
}