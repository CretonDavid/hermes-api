package com.hermes.api.model;

import com.hermes.api.model.enums.PaymentMethod;
import com.hermes.api.model.enums.RecurrenceFrequency;
import com.hermes.api.model.enums.ReimbursementPeriod;
import com.hermes.api.model.enums.TransactionDirection;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Modèle d'opération récurrente. Si {@code targetAccount} est renseigné, il s'agit d'un virement
 * récurrent de {@code account} vers {@code targetAccount}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "recurring_transaction")
@SequenceGenerator(name = "recurring_transaction_seq", sequenceName = "recurring_transaction_seq", allocationSize = 10)
public class RecurringTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "recurring_transaction_seq")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_account_id")
    private Account targetAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private TransactionDirection direction;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    // Montant crédité sur le compte cible si sa devise diffère
    @Column(name = "target_amount", precision = 19, scale = 2)
    private BigDecimal targetAmount;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "payee")
    private String payee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 10)
    private RecurrenceFrequency frequency;

    @Column(name = "interval_count", nullable = false)
    private int interval;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    // null quand la récurrence est terminée
    @Column(name = "next_date")
    private LocalDate nextDate;

    @Column(name = "occurrence_count", nullable = false)
    private int occurrenceCount;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Tiers qui rembourse cette dépense (abonnement partagé par exemple), null si personne
    @Column(name = "reimbursement_payer", length = 100)
    private String reimbursementPayer;

    // Part remboursée, en pourcentage
    @Column(name = "reimbursement_share")
    private Integer reimbursementShare;

    @Enumerated(EnumType.STRING)
    @Column(name = "reimbursement_period", length = 10)
    private ReimbursementPeriod reimbursementPeriod;

    public boolean isTransfer() {
        return targetAccount != null;
    }

    public boolean isReimbursed() {
        return reimbursementPayer != null;
    }
}
