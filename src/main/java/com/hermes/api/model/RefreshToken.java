package com.hermes.api.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Refresh token émis par l'API, identifié par le jti du JWT. Chaque rafraîchissement le remplace
 * par un nouveau : présenter un jeton déjà remplacé révèle un vol et coupe toutes les sessions.
 */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "refresh_tokens", indexes = @Index(name = "idx_refresh_tokens_user", columnList = "user_id"))
public class RefreshToken {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    // Renseigné quand le jeton a été échangé contre un nouveau
    @Column(name = "rotated_at")
    private Instant rotatedAt;
}
