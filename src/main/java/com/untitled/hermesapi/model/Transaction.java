package com.untitled.hermesapi.model;

import com.untitled.hermesapi.model.enums.PaymentMethod;
import com.untitled.hermesapi.model.enums.TransactionDirection;
import com.untitled.hermesapi.model.enums.TransactionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_transactions_account_date", columnList = "account_id, operation_date"),
        @Index(name = "idx_transactions_import_id", columnList = "account_id, import_id")
})
@SequenceGenerator(name = "transaction_seq", sequenceName = "transaction_seq", allocationSize = 50)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "transaction_seq")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private TransactionDirection direction;

    // Toujours positif, le sens est porté par direction
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "label", nullable = false)
    private String label;

    // Tiers : bénéficiaire d'une dépense ou émetteur d'un revenu
    @Column(name = "payee")
    private String payee;

    @Column(name = "notes", length = 1000)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;

    // Date de l'opération : date de référence pour les soldes et les statistiques
    @Column(name = "operation_date", nullable = false)
    private LocalDate operationDate;

    // Date de valeur appliquée par la banque
    @Column(name = "value_date")
    private LocalDate valueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;

    // Opération miroir d'un virement entre deux comptes
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_peer_id")
    private Transaction transferPeer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recurring_transaction_id")
    private RecurringTransaction recurringTransaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reconciliation_id")
    private Reconciliation reconciliation;

    // Identifiant de l'opération dans le fichier importé (FITID OFX ou empreinte CSV)
    @Column(name = "import_id")
    private String importId;

    // Date de saisie
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public boolean isTransfer() {
        return transferPeer != null;
    }

    public BigDecimal signedAmount() {
        return direction == TransactionDirection.CREDIT ? amount : amount.negate();
    }
}
