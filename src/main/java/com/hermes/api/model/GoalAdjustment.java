package com.hermes.api.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Mouvement d'argent libre du compte vers un budget spécial (montant positif), ou d'un budget
 * spécial vers l'argent libre (montant négatif), sans opération bancaire.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "goal_adjustment", indexes = @Index(name = "idx_goal_adjustment_goal", columnList = "goal_id"))
@SequenceGenerator(name = "goal_adjustment_seq", sequenceName = "goal_adjustment_seq", allocationSize = 10)
public class GoalAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "goal_adjustment_seq")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goal_id", nullable = false)
    private SavingsGoal goal;

    @Column(name = "adjustment_date", nullable = false)
    private LocalDate date;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
