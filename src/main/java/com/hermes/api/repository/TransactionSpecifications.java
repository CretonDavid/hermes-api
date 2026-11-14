package com.hermes.api.repository;

import com.hermes.api.dto.transaction.TransactionFilter;
import com.hermes.api.model.Transaction;
import com.hermes.api.model.TransactionSplit;
import com.hermes.api.model.enums.TransactionDirection;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    /**
     * @param categoryIds catégories déjà étendues à leurs sous-catégories
     */
    public static Specification<Transaction> matching(UUID ownerId, TransactionFilter filter, Collection<Long> categoryIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("account").get("owner").get("id"), ownerId));

            if (filter.accountIds() != null && !filter.accountIds().isEmpty()) {
                predicates.add(root.get("account").get("id").in(filter.accountIds()));
            }
            if (filter.recurringTransactionId() != null) {
                predicates.add(cb.equal(root.get("recurringTransaction").get("id"), filter.recurringTransactionId()));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("operationDate"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("operationDate"), filter.to()));
            }

            // Une opération ventilée correspond si l'une de ses lignes correspond
            List<Predicate> categoryPredicates = new ArrayList<>();
            if (categoryIds != null && !categoryIds.isEmpty()) {
                Subquery<Long> split = query.subquery(Long.class);
                Root<TransactionSplit> s = split.from(TransactionSplit.class);
                split.select(s.get("id")).where(
                        cb.equal(s.get("transaction"), root),
                        s.get("category").get("id").in(categoryIds));
                categoryPredicates.add(root.get("category").get("id").in(categoryIds));
                categoryPredicates.add(cb.exists(split));
            }
            if (Boolean.TRUE.equals(filter.uncategorized())) {
                Subquery<Long> uncategorizedSplit = query.subquery(Long.class);
                Root<TransactionSplit> s = uncategorizedSplit.from(TransactionSplit.class);
                uncategorizedSplit.select(s.get("id")).where(
                        cb.equal(s.get("transaction"), root),
                        cb.isNull(s.get("category")));
                categoryPredicates.add(cb.and(
                        cb.isNull(root.get("category")),
                        cb.isNull(root.get("transferPeer")),
                        cb.or(cb.isEmpty(root.get("splits")), cb.exists(uncategorizedSplit))));
            }
            if (!categoryPredicates.isEmpty()) {
                predicates.add(cb.or(categoryPredicates.toArray(Predicate[]::new)));
            }

            if (filter.kind() != null) {
                switch (filter.kind()) {
                    case TRANSFER -> predicates.add(cb.isNotNull(root.get("transferPeer")));
                    case EXPENSE -> {
                        predicates.add(cb.isNull(root.get("transferPeer")));
                        predicates.add(cb.equal(root.get("direction"), TransactionDirection.DEBIT));
                    }
                    case INCOME -> {
                        predicates.add(cb.isNull(root.get("transferPeer")));
                        predicates.add(cb.equal(root.get("direction"), TransactionDirection.CREDIT));
                    }
                }
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.search() != null && !filter.search().isBlank()) {
                String pattern = LikePatterns.containsIgnoreCase(filter.search());
                // Le libellé d'une ligne de ventilation est aussi recherché
                Subquery<Long> splitLabel = query.subquery(Long.class);
                Root<TransactionSplit> s = splitLabel.from(TransactionSplit.class);
                splitLabel.select(s.get("id")).where(
                        cb.equal(s.get("transaction"), root),
                        cb.like(cb.lower(cb.coalesce(s.get("label"), "")), pattern, LikePatterns.ESCAPE));
                predicates.add(cb.or(
                        cb.exists(splitLabel),
                        cb.like(cb.lower(root.get("label")), pattern, LikePatterns.ESCAPE),
                        cb.like(cb.lower(cb.coalesce(root.get("payee"), "")), pattern, LikePatterns.ESCAPE),
                        cb.like(cb.lower(cb.coalesce(root.get("notes"), "")), pattern, LikePatterns.ESCAPE)
                ));
            }
            if (filter.minAmount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), filter.minAmount()));
            }
            if (filter.maxAmount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("amount"), filter.maxAmount()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
