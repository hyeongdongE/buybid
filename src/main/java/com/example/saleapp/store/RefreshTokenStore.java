package com.example.saleapp.store;

public interface RefreshTokenStore {
    void save(String email, String token, long ttlMillis);
    void save(String email, String newRefreshToken);

    String find(String email);
    void delete(String email);
}