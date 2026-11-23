package com.example.saleapp.component;

import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtTokenProvider {
    public static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    public static final Duration REFRESH_TTL = Duration.ofDays(7);

    private final Key key;
    private final JwtParser parser;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret) {
        key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        parser = Jwts.parserBuilder().setSigningKey(key).requireIssuer("buybid").build();
    }

    public String createAccessToken(String email) {
        return issue(email, "access", ACCESS_TTL);
    }

    public String createRefreshToken(String email) {
        return issue(email, "refresh", REFRESH_TTL);
    }

    public Optional<AccessSession> accessSession(String token) {
        return claims(token, "access")
                .map(claims -> new AccessSession(claims.getSubject(), claims.getExpiration().toInstant()));
    }

    public Optional<String> refreshSubject(String token) {
        return claims(token, "refresh").map(Claims::getSubject);
    }

    public record AccessSession(String email, Instant expiresAt) {}

    private String issue(String email, String purpose, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder().setIssuer("buybid").setSubject(email)
                .setId(UUID.randomUUID().toString()).claim("purpose", purpose)
                .setIssuedAt(Date.from(now)).setExpiration(Date.from(now.plus(ttl)))
                .signWith(key, SignatureAlgorithm.HS256).compact();
    }

    private Optional<Claims> claims(String token, String purpose) {
        try {
            Jws<Claims> parsed = parser.parseClaimsJws(token);
            Claims claims = parsed.getBody();
            if (!"HS256".equals(parsed.getHeader().getAlgorithm()) || claims.getExpiration() == null
                    || !purpose.equals(claims.get("purpose", String.class))) {
                return Optional.empty();
            }
            return Optional.ofNullable(claims.getSubject()).filter(subject -> !subject.isBlank()).map(subject -> claims);
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
