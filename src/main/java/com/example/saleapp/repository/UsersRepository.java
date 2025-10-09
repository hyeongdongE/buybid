package com.example.saleapp.repository;

import com.example.saleapp.entity.UsersEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsersRepository extends JpaRepository<UsersEntity, Long> {
    boolean existsByEmail(String email);
    Optional<UsersEntity> findByEmail(String email);
}
