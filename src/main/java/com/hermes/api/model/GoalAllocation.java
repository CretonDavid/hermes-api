package com.hermes.api.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Part d'un crédit (virement entrant, intérêts…) affectée à un budget spécial du même compte.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "goal_allocation", indexes = {
        @Index(name = "idx_goal_allocation_transaction", columnList = "transaction_id"),
        @Index(name = "idx_goal_allocation_goal", columnList = "goal_id")
})
@SequenceGenerator(name = "goal_allocation_seq", sequenceName = "goal_allocation_seq", allocationSize = 50)
public class GoalAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "goal_allocation_seq")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goal_id", nullable = false)
    private SavingsGoal goal;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
}
