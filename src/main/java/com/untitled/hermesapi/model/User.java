package com.untitled.hermesapi.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Les 6 premiers chiffres du numéro de compte, utilisés pour retrouver l'utilisateur
    @Column(name = "lookup_key", nullable = false, unique = true, length = 6)
    private String lookupKey;

    // Hash Argon2 du numéro de compte complet
    @Column(name = "account_number_hash", nullable = false)
    private String accountNumberHash;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reference_currency_id")
    private Currency referenceCurrency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
}
