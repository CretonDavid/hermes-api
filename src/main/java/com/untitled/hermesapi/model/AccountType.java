package com.untitled.hermesapi.model;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "account_type")
@SequenceGenerator(name = "account_type_seq", sequenceName = "account_type_seq", allocationSize = 10)
public class AccountType {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "account_type_seq")
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    // Les virements vers un compte de ce type sont comptés comme de l'épargne
    @Column(name = "savings", nullable = false)
    private boolean savings;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;
}
