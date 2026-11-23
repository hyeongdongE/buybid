package com.example.saleapp.repository;

import com.example.saleapp.entity.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, String> {
    @Transactional
    @Modifying
    @Query("update RefreshTokenEntity t set t.token = :replacement where t.email = :email and t.token = :previous")
    int rotate(String email, String previous, String replacement);

    @Transactional
    @Modifying
    @Query("delete from RefreshTokenEntity t where t.email = :email and t.token = :value")
    int revoke(String email, String value);
}
