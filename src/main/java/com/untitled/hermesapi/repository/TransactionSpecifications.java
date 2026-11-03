package com.untitled.hermesapi.repository;

import com.untitled.hermesapi.dto.transaction.TransactionFilter;
import com.untitled.hermesapi.model.Transaction;
import com.untitled.hermesapi.model.enums.TransactionDirection;
import jakarta.persistence.criteria.Predicate;
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
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("operationDate"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("operationDate"), filter.to()));
            }

            List<Predicate> categoryPredicates = new ArrayList<>();
            if (categoryIds != null && !categoryIds.isEmpty()) {
                categoryPredicates.add(root.get("category").get("id").in(categoryIds));
            }
            if (Boolean.TRUE.equals(filter.uncategorized())) {
                categoryPredicates.add(cb.and(cb.isNull(root.get("category")), cb.isNull(root.get("transferPeer"))));
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
                String pattern = "%" + filter.search().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("label")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("payee"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("notes"), "")), pattern)
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
