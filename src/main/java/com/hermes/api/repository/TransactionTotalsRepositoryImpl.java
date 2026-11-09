package com.hermes.api.repository;

import com.hermes.api.dto.transaction.TransactionTotals;
import com.hermes.api.model.Transaction;
import com.hermes.api.model.enums.TransactionDirection;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;

@RequiredArgsConstructor
public class TransactionTotalsRepositoryImpl implements TransactionTotalsRepository {

    private final EntityManager entityManager;

    @Override
    public List<TransactionTotals> totalsByCurrency(Specification<Transaction> specification) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Transaction> root = query.from(Transaction.class);

        Path<String> currency = root.get("account").get("currency").get("code");
        Expression<Long> count = cb.count(root);
        Expression<BigDecimal> credits = cb.sum(amountWhen(cb, root, TransactionDirection.CREDIT));
        Expression<BigDecimal> debits = cb.sum(amountWhen(cb, root, TransactionDirection.DEBIT));

        query.select(cb.tuple(currency, count, credits, debits))
                .where(specification.toPredicate(root, query, cb))
                .groupBy(currency)
                .orderBy(cb.asc(currency));

        return entityManager.createQuery(query).getResultList().stream()
                .map(row -> {
                    BigDecimal credit = zeroIfNull(row.get(credits));
                    BigDecimal debit = zeroIfNull(row.get(debits));
                    return new TransactionTotals(row.get(currency), row.get(count), credit, debit,
                            credit.subtract(debit));
                })
                .toList();
    }

    private static Expression<BigDecimal> amountWhen(CriteriaBuilder cb, Root<Transaction> root, TransactionDirection direction) {
        return cb.<BigDecimal>selectCase()
                .when(cb.equal(root.get("direction"), direction), root.<BigDecimal>get("amount"))
                .otherwise(BigDecimal.ZERO);
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
