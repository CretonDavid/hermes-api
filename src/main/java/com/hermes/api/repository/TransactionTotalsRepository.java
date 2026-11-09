package com.hermes.api.repository;

import com.hermes.api.dto.transaction.TransactionTotals;
import com.hermes.api.model.Transaction;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

/**
 * Agrégats calculés par la base sur un ensemble d'opérations filtrées.
 */
public interface TransactionTotalsRepository {

    /**
     * Nombre d'opérations, crédits, débits et solde, par devise, triés par code de devise.
     */
    List<TransactionTotals> totalsByCurrency(Specification<Transaction> specification);
}
