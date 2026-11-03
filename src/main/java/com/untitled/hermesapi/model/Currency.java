package com.untitled.hermesapi.model;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "currencies")
@SequenceGenerator(name = "currency_seq", sequenceName = "currency_seq", allocationSize = 10)
public class Currency {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "currency_seq")
    private Long id;

    @Column(name = "code", nullable = false, length = 3)
    private String code;

    @Column(name = "symbol", nullable = false, length = 10)
    private String symbol;

    @Column(name = "name", nullable = false)
    private String name;

    // null pour les devises par défaut, partagées par tous les utilisateurs
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    public boolean isCustom() {
        return owner != null;
    }
}
