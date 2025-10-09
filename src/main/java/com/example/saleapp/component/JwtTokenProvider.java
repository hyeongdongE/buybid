package com.example.saleapp.component;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Collection;
import java.util.Date;

@Component
@Slf4j
public class JwtTokenProvider {
    private final Key key;

    public JwtTokenProvider(@Value("${jwt.secret}") String secretKey) {
        this.key = Keys.hmacShaKeyFor(io.jsonwebtoken.io.Decoders.BASE64.decode(secretKey));
    }

    private final long ACCESS_TOKEN = 1000L * 60 * 15;
    private final long REFRESH_TOKEN = 1000L * 60 * 60 * 24 * 7;

    public String createAccessToken(String email, Collection<? extends GrantedAuthority> roles) {
        long now = System.currentTimeMillis();

        String token = Jwts.builder()
                .setSubject(email)
                .claim("roles", roles.stream().map(GrantedAuthority::getAuthority).toList())
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + ACCESS_TOKEN))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        return token;
    }

    public String createRefreshToken(String email) {
        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + REFRESH_TOKEN))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String getEmail(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token).getBody().getSubject();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.info("만료된 토큰입니다.");
        } catch (JwtException | IllegalArgumentException e) {
            log.info("JWT 검증 실패: {}", e.getMessage());
        }
        return false;
    }
}