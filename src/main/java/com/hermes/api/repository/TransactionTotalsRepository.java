package com.hermes.api.repository;

import com.hermes.api.dto.transaction.TransactionTotals;
import com.hermes.api.model.Transaction;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.List;

/**
 * Agrégats calculés par la base sur un ensemble d'opérations filtrées.
 */
public interface TransactionTotalsRepository {

    /**
     * Nombre d'opérations, crédits, débits et solde, par devise, triés par code de devise. Avec un
     * filtre de catégories, une opération ventilée ne compte que pour ses lignes qui y correspondent.
     *
     * @param categoryIds   catégories du filtre, déjà étendues à leurs sous-catégories
     * @param uncategorized le filtre inclut les opérations non catégorisées
     */
    List<TransactionTotals> totalsByCurrency(Specification<Transaction> specification,
                                             Collection<Long> categoryIds, boolean uncategorized);
}
