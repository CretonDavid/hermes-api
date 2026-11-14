package com.hermes.api.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Budget spécial : un objectif d'épargne (une moto par exemple) mis de côté sur un compte bancaire.
 * <p>
 * Son montant n'est pas stocké : il se calcule en rejouant l'historique du compte (montant de
 * départ, crédits affectés, retraits qui font passer le solde sous le total des budgets du compte).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "savings_goal")
@SequenceGenerator(name = "savings_goal_seq", sequenceName = "savings_goal_seq", allocationSize = 10)
public class SavingsGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "savings_goal_seq")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "target_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal targetAmount;

    // Déjà mis de côté sur le compte à la création, compté au début de startDate
    @Column(name = "initial_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal initialAmount;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    // Jour où le budget a été utilisé (la moto achetée) : son montant est libéré
    // ce jour-là avant les retraits du jour
    @Column(name = "used_date")
    private LocalDate usedDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public boolean isUsed() {
        return usedDate != null;
    }
}
