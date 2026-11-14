package com.hermes.api.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Part d'une opération ventilée sur plusieurs catégories. La somme des parts est égale au montant
 * de l'opération, et chaque part suit le sens de l'opération.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "transaction_splits", indexes = {
        @Index(name = "idx_transaction_splits_transaction", columnList = "transaction_id"),
        @Index(name = "idx_transaction_splits_category", columnList = "category_id")
})
@SequenceGenerator(name = "transaction_split_seq", sequenceName = "transaction_split_seq", allocationSize = 50)
public class TransactionSplit {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "transaction_split_seq")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    // Null si la catégorie a été supprimée sans remplacement : la part devient non catégorisée
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    // Toujours positif, le sens est celui de l'opération
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
}
