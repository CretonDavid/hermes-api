package com.hermes.api.repository;

import com.hermes.api.dto.transaction.TransactionTotals;
import com.hermes.api.model.Transaction;
import com.hermes.api.model.TransactionSplit;
import com.hermes.api.model.enums.TransactionDirection;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@RequiredArgsConstructor
public class TransactionTotalsRepositoryImpl implements TransactionTotalsRepository {

    private final EntityManager entityManager;

    @Override
    public List<TransactionTotals> totalsByCurrency(Specification<Transaction> specification,
                                                    Collection<Long> categoryIds, boolean uncategorized) {
        if (categoryIds.isEmpty() && !uncategorized) {
            return List.copyOf(wholeTransactions(specification, false).values());
        }
        // Les opérations ventilées ne comptent que pour leurs lignes qui correspondent au filtre
        Map<String, TransactionTotals> totals = wholeTransactions(specification, true);
        matchingSplits(specification, categoryIds, uncategorized).forEach((currency, part) ->
                totals.merge(currency, part, (a, b) -> new TransactionTotals(currency, a.count() + b.count(),
                        a.credits().add(b.credits()), a.debits().add(b.debits()), a.net().add(b.net()))));
        return new ArrayList<>(totals.values());
    }

    private Map<String, TransactionTotals> wholeTransactions(Specification<Transaction> specification, boolean withoutSplits) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Transaction> root = query.from(Transaction.class);

        Path<String> currency = root.get("account").get("currency").get("code");
        Path<TransactionDirection> direction = root.get("direction");
        Path<BigDecimal> amount = root.get("amount");
        Predicate where = specification.toPredicate(root, query, cb);
        if (withoutSplits) {
            where = cb.and(where, cb.isEmpty(root.get("splits")));
        }
        return run(cb, query, currency, cb.count(root), direction, amount, where);
    }

    private Map<String, TransactionTotals> matchingSplits(Specification<Transaction> specification,
                                                         Collection<Long> categoryIds, boolean uncategorized) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Transaction> root = query.from(Transaction.class);
        Join<Transaction, TransactionSplit> split = root.join("splits");

        List<Predicate> lines = new ArrayList<>();
        if (!categoryIds.isEmpty()) {
            lines.add(split.get("category").get("id").in(categoryIds));
        }
        if (uncategorized) {
            lines.add(cb.isNull(split.get("category")));
        }
        Predicate where = cb.and(specification.toPredicate(root, query, cb), cb.or(lines.toArray(Predicate[]::new)));
        return run(cb, query, root.get("account").get("currency").get("code"), cb.countDistinct(root),
                root.get("direction"), split.get("amount"), where);
    }

    private Map<String, TransactionTotals> run(CriteriaBuilder cb, CriteriaQuery<Tuple> query, Path<String> currency,
                                               Expression<Long> count, Path<TransactionDirection> direction,
                                               Path<BigDecimal> amount, Predicate where) {
        Expression<BigDecimal> credits = cb.sum(amountWhen(cb, direction, amount, TransactionDirection.CREDIT));
        Expression<BigDecimal> debits = cb.sum(amountWhen(cb, direction, amount, TransactionDirection.DEBIT));
        query.select(cb.tuple(currency, count, credits, debits)).where(where).groupBy(currency);

        Map<String, TransactionTotals> totals = new TreeMap<>();
        for (Tuple row : entityManager.createQuery(query).getResultList()) {
            BigDecimal credit = zeroIfNull(row.get(credits));
            BigDecimal debit = zeroIfNull(row.get(debits));
            totals.put(row.get(currency), new TransactionTotals(row.get(currency), row.get(count), credit, debit,
                    credit.subtract(debit)));
        }
        return totals;
    }

    private static Expression<BigDecimal> amountWhen(CriteriaBuilder cb, Path<TransactionDirection> direction,
                                                     Path<BigDecimal> amount, TransactionDirection expected) {
        return cb.<BigDecimal>selectCase()
                .when(cb.equal(direction, expected), amount)
                .otherwise(BigDecimal.ZERO);
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
