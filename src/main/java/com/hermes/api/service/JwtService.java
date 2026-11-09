package com.hermes.api.service;

import com.hermes.api.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String CLAIM_TYPE = "type";

    public enum TokenType {
        ACCESS("access"),
        REFRESH("refresh");

        private final String claim;

        TokenType(String claim) {
            this.claim = claim;
        }
    }

    /**
     * Contenu vérifié d'un jeton : utilisateur, identifiant unique (jti) et expiration.
     */
    public record VerifiedToken(UUID userId, UUID tokenId, Instant expiresAt) {
    }

    /**
     * Jeton signé, avec son identifiant et son expiration pour l'enregistrer côté serveur.
     */
    public record IssuedToken(String value, UUID tokenId, Instant expiresAt) {
    }

    private final SecretKey secretKey;
    private final JwtProperties jwtProperties;

    public IssuedToken generateAccessToken(UUID userId) {
        return buildToken(userId, TokenType.ACCESS, jwtProperties.accessTokenTtl());
    }

    public IssuedToken generateRefreshToken(UUID userId) {
        return buildToken(userId, TokenType.REFRESH, jwtProperties.refreshTokenTtl());
    }

    /**
     * Vérifie la signature, l'émetteur, l'expiration et le type du jeton, en un seul décodage.
     */
    public Optional<VerifiedToken> verify(String token, TokenType expectedType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(jwtProperties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!expectedType.claim.equals(claims.get(CLAIM_TYPE, String.class))) {
                return Optional.empty();
            }
            return Optional.of(new VerifiedToken(
                    UUID.fromString(claims.getSubject()),
                    UUID.fromString(claims.getId()),
                    claims.getExpiration().toInstant()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private IssuedToken buildToken(UUID userId, TokenType type, Duration ttl) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        UUID tokenId = UUID.randomUUID();
        String value = Jwts.builder()
                .subject(userId.toString())
                .id(tokenId.toString())
                .issuer(jwtProperties.issuer())
                .claim(CLAIM_TYPE, type.claim)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(value, tokenId, expiresAt);
    }
}
