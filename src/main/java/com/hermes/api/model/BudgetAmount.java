package com.hermes.api.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Montant d'une enveloppe de budget : à partir d'un mois, ou pour ce mois seulement.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "budget_amount")
@SequenceGenerator(name = "budget_amount_seq", sequenceName = "budget_amount_seq", allocationSize = 10)
public class BudgetAmount {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "budget_amount_seq")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "envelope_id", nullable = false)
    private BudgetEnvelope envelope;

    // Premier jour du mois concerné
    @Column(name = "budget_month", nullable = false)
    private LocalDate month;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    // Vrai pour une exception propre à ce mois, faux pour un montant valable à partir de ce mois
    @Column(name = "month_only", nullable = false)
    private boolean monthOnly;
}
