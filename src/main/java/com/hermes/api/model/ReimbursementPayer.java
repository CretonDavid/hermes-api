package com.hermes.api.model;

import com.hermes.api.model.enums.ReimbursementPeriod;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tiers qui rembourse une part d'une dépense récurrente (abonnement partagé par exemple).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "reimbursement_payer")
@SequenceGenerator(name = "reimbursement_payer_seq", sequenceName = "reimbursement_payer_seq", allocationSize = 10)
public class ReimbursementPayer {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "reimbursement_payer_seq")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recurring_transaction_id", nullable = false)
    private RecurringTransaction recurringTransaction;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    // Part remboursée, en pourcentage du montant de la dépense
    @Column(name = "share", nullable = false, precision = 5, scale = 2)
    private BigDecimal share;

    @Enumerated(EnumType.STRING)
    @Column(name = "period", nullable = false, length = 10)
    private ReimbursementPeriod period;

    // Premier jour de la première période due ; null pour les tiers créés avant ce champ
    @Column(name = "start_date")
    private LocalDate startDate;

    // Première période due : celle choisie, ou à défaut celle du début de la récurrence
    public LocalDate firstPeriod() {
        return period.start(startDate != null ? startDate : recurringTransaction.getStartDate());
    }
}
