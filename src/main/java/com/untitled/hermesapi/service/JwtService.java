package com.untitled.hermesapi.service;

import com.untitled.hermesapi.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String CLAIM_TYPE = "type";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    private final SecretKey secretKey;
    private final JwtProperties jwtProperties;

    public String generateAccessToken(UUID userId) {
        return buildToken(userId, TOKEN_TYPE_ACCESS, jwtProperties.accessTokenTtl());
    }

    public String generateRefreshToken(UUID userId) {
        return buildToken(userId, TOKEN_TYPE_REFRESH, jwtProperties.refreshTokenTtl());
    }

    private String buildToken(UUID userId, String type, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .issuer(jwtProperties.issuer())
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public Jws<Claims> parse(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(jwtProperties.issuer())
                .build()
                .parseSignedClaims(token);
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(parse(token).getPayload().getSubject());
    }

    public boolean isAccessToken(String token) {
        return TOKEN_TYPE_ACCESS.equals(parse(token).getPayload().get(CLAIM_TYPE, String.class));
    }

    public boolean isRefreshToken(String token) {
        return TOKEN_TYPE_REFRESH.equals(parse(token).getPayload().get(CLAIM_TYPE, String.class));
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}