package com.hermes.api.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Enveloppe de budget mensuelle d'une catégorie de dépense (sous-catégories comprises).
 * <p>
 * Son montant d'un mois est celui fixé pour ce mois seulement s'il existe, sinon le dernier montant
 * fixé « à partir de » ce mois ou d'un mois antérieur. Avant son premier montant, l'enveloppe
 * n'existe pas.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "budget_envelope", uniqueConstraints =
        @UniqueConstraint(name = "uk_budget_envelope_category", columnNames = "category_id"))
@SequenceGenerator(name = "budget_envelope_seq", sequenceName = "budget_envelope_seq", allocationSize = 10)
public class BudgetEnvelope {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "budget_envelope_seq")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @OneToMany(mappedBy = "envelope", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("month")
    @Builder.Default
    private List<BudgetAmount> amounts = new ArrayList<>();

    /** Montant fixé pour ce mois seulement */
    public Optional<BudgetAmount> monthOnlyAmount(LocalDate month) {
        return amounts.stream().filter(a -> a.isMonthOnly() && a.getMonth().equals(month)).findFirst();
    }

    /** Montant habituel du mois : le dernier fixé « à partir de » ce mois ou d'un mois antérieur */
    public Optional<BudgetAmount> usualAmount(LocalDate month) {
        return amounts.stream()
                .filter(a -> !a.isMonthOnly() && !a.getMonth().isAfter(month))
                .reduce((first, second) -> second.getMonth().isAfter(first.getMonth()) ? second : first);
    }

    /** Montant du mois, vide si l'enveloppe n'existait pas encore */
    public Optional<BudgetAmount> amountFor(LocalDate month) {
        return monthOnlyAmount(month).or(() -> usualAmount(month));
    }
}
