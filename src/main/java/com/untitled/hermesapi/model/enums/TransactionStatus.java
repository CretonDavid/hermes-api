package com.untitled.hermesapi.model.enums;

/**
 * PENDING : saisie, non pointée.
 * CLEARED : pointée (vue sur le relevé bancaire).
 * RECONCILED : rapprochée, verrouillée par un rapprochement bancaire.
 */
public enum TransactionStatus {
    PENDING,
    CLEARED,
    RECONCILED
}
