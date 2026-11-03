package com.untitled.hermesapi.repository;

import com.untitled.hermesapi.model.Category;
import com.untitled.hermesapi.model.Reconciliation;
import com.untitled.hermesapi.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    String SIGNED_AMOUNT = "(case when t.direction = com.untitled.hermesapi.model.enums.TransactionDirection.CREDIT then t.amount else -t.amount end)";

    interface AccountBalanceView {
        Long getAccountId();

        BigDecimal getTotal();

        BigDecimal getCurrent();

        BigDecimal getCleared();
    }

    interface AccountSumView {
        Long getAccountId();

        BigDecimal getTotal();
    }

    interface PayeeView {
        String getPayee();

        Long getUsage();
    }

    @EntityGraph(attributePaths = {"account", "account.currency", "category", "transferPeer", "transferPeer.account"})
    Page<Transaction> findAll(Specification<Transaction> specification, Pageable pageable);

    @Query("""
            select t from Transaction t
            join fetch t.account a join fetch a.currency
            left join fetch t.category
            left join fetch t.transferPeer p left join fetch p.account
            where t.id = :id and a.owner.id = :ownerId
            """)
    Optional<Transaction> findByIdAndOwner(Long id, UUID ownerId);

    // Opérations d'une période, avec tout ce qu'il faut pour les statistiques
    @Query("""
            select t from Transaction t
            join fetch t.account a join fetch a.currency join fetch a.accountType
            left join fetch t.category
            left join fetch t.transferPeer p left join fetch p.account pa left join fetch pa.accountType
            where a.owner.id = :ownerId and t.operationDate between :from and :to
            """)
    List<Transaction> findForStats(UUID ownerId, LocalDate from, LocalDate to);

    @Query("select t.account.id as accountId, sum(" + SIGNED_AMOUNT + ") as total from Transaction t "
            + "where t.account.owner.id = :ownerId and t.operationDate < :before group by t.account.id")
    List<AccountSumView> sumByAccountBefore(UUID ownerId, LocalDate before);

    @Query("select t.account.id as accountId, "
            + "sum(" + SIGNED_AMOUNT + ") as total, "
            + "sum(case when t.operationDate <= :today then " + SIGNED_AMOUNT + " else 0 end) as current, "
            + "sum(case when t.status <> com.untitled.hermesapi.model.enums.TransactionStatus.PENDING then " + SIGNED_AMOUNT + " else 0 end) as cleared "
            + "from Transaction t where t.account.owner.id = :ownerId group by t.account.id")
    List<AccountBalanceView> balancesByOwner(UUID ownerId, LocalDate today);

    @Query("select coalesce(sum(" + SIGNED_AMOUNT + "), 0) from Transaction t "
            + "where t.account.id = :accountId and t.status <> com.untitled.hermesapi.model.enums.TransactionStatus.PENDING")
    BigDecimal clearedSum(Long accountId);

    @Query("select count(t) from Transaction t where t.account.id = :accountId "
            + "and t.status = com.untitled.hermesapi.model.enums.TransactionStatus.CLEARED")
    long countCleared(Long accountId);

    @Query("""
            select t.payee as payee, count(t) as usage from Transaction t
            where t.account.owner.id = :ownerId and t.payee is not null and t.transferPeer is null
              and lower(t.payee) like lower(concat('%', :query, '%'))
            group by t.payee order by count(t) desc
            """)
    List<PayeeView> findPayees(UUID ownerId, String query, Pageable pageable);

    // Opérations catégorisées d'un utilisateur, les plus récentes d'abord (suggestions de catégorie)
    @Query("""
            select t from Transaction t join fetch t.category
            where t.account.owner.id = :ownerId and t.transferPeer is null
            order by t.operationDate desc, t.id desc
            """)
    List<Transaction> findCategorizedByOwner(UUID ownerId, Pageable pageable);

    @Query("select t from Transaction t where t.account.id = :accountId and t.operationDate between :from and :to")
    List<Transaction> findByAccountBetween(Long accountId, LocalDate from, LocalDate to);

    @Query("select t.importId from Transaction t where t.account.id = :accountId and t.importId is not null")
    List<String> findImportIds(Long accountId);

    @Query("select max(t.operationDate) from Transaction t where t.recurringTransaction.id = :recurringId")
    Optional<LocalDate> findLastOperationDateForRecurring(Long recurringId);

    @Modifying(flushAutomatically = true)
    @Query("update Transaction t set t.status = com.untitled.hermesapi.model.enums.TransactionStatus.RECONCILED, "
            + "t.reconciliation = :reconciliation where t.account.id = :accountId "
            + "and t.status = com.untitled.hermesapi.model.enums.TransactionStatus.CLEARED")
    int markReconciled(Long accountId, Reconciliation reconciliation);

    @Modifying(flushAutomatically = true)
    @Query("update Transaction t set t.status = com.untitled.hermesapi.model.enums.TransactionStatus.CLEARED, "
            + "t.reconciliation = null where t.reconciliation.id = :reconciliationId")
    int undoReconciliation(Long reconciliationId);

    @Modifying(flushAutomatically = true)
    @Query("update Transaction t set t.category = null where t.category.id in :categoryIds")
    void clearCategory(List<Long> categoryIds);

    @Modifying(flushAutomatically = true)
    @Query("update Transaction t set t.category = :replacement where t.category.id in :categoryIds")
    void replaceCategory(List<Long> categoryIds, Category replacement);

    @Modifying(flushAutomatically = true)
    @Query("update Transaction t set t.recurringTransaction = null where t.recurringTransaction.id = :recurringId")
    void detachRecurring(Long recurringId);

    @Modifying(flushAutomatically = true)
    @Query("update Transaction t set t.recurringTransaction = null where t.recurringTransaction.id in "
            + "(select r.id from RecurringTransaction r where r.account.id = :accountId or r.targetAccount.id = :accountId)")
    void detachRecurringOfAccount(Long accountId);

    // Les virements liés à un compte supprimé deviennent de simples opérations sur l'autre compte
    @Modifying(flushAutomatically = true)
    @Query("update Transaction t set t.transferPeer = null where t.transferPeer.id in "
            + "(select x.id from Transaction x where x.account.id = :accountId) or t.account.id = :accountId")
    void unlinkTransfersOfAccount(Long accountId);

    @Modifying(flushAutomatically = true)
    @Query("delete from Transaction t where t.account.id = :accountId")
    void deleteByAccountId(Long accountId);

    @Modifying(flushAutomatically = true)
    @Query("update Transaction t set t.transferPeer = null, t.recurringTransaction = null, t.reconciliation = null "
            + "where t.account.id in (select a.id from Account a where a.owner.id = :ownerId)")
    void unlinkAllByOwnerId(UUID ownerId);

    @Modifying(flushAutomatically = true)
    @Query("delete from Transaction t where t.account.id in (select a.id from Account a where a.owner.id = :ownerId)")
    void deleteByOwnerId(UUID ownerId);
}
