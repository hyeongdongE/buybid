package com.example.saleapp.service;

import com.example.saleapp.entity.RefreshTokenEntity;
import com.example.saleapp.repository.RefreshTokenRepository;
import com.example.saleapp.store.RefreshTokenStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenAbapter implements RefreshTokenStore {
    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    public void save(String email, String token, long ttlMillis) {
        refreshTokenRepository.save(new RefreshTokenEntity(email, token));
    }

    @Override
    public void save(String email, String token) {
        refreshTokenRepository.save(new RefreshTokenEntity(email, token));
    }

    @Override
    public String find(String email) {
        String token = refreshTokenRepository.findByEmail(email)
                .map(RefreshTokenEntity::getToken)
                .orElse(null);

        return token;
    }

    @Override
    public void delete(String email) {
        refreshTokenRepository.deleteById(email);
    }
}
