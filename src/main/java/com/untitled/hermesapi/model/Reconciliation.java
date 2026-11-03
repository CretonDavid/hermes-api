package com.untitled.hermesapi.model;

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
@Table(name = "reconciliation")
@SequenceGenerator(name = "reconciliation_seq", sequenceName = "reconciliation_seq", allocationSize = 10)
public class Reconciliation {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "reconciliation_seq")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "statement_date", nullable = false)
    private LocalDate statementDate;

    @Column(name = "statement_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal statementBalance;

    @Column(name = "transaction_count", nullable = false)
    private int transactionCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
