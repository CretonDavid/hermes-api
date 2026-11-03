package com.untitled.hermesapi.repository;

import com.untitled.hermesapi.model.Category;
import com.untitled.hermesapi.model.RecurringTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, Long> {

    @Query("""
            select r from RecurringTransaction r
            join fetch r.account a join fetch a.currency
            left join fetch r.targetAccount ta left join fetch ta.currency
            left join fetch r.category
            where r.owner.id = :ownerId
            order by r.active desc, r.nextDate asc, r.label asc
            """)
    List<RecurringTransaction> findAllByOwner(UUID ownerId);

    @Query("""
            select r from RecurringTransaction r
            join fetch r.account a join fetch a.currency
            left join fetch r.targetAccount ta left join fetch ta.currency
            left join fetch r.category
            where r.id = :id and r.owner.id = :ownerId
            """)
    Optional<RecurringTransaction> findByIdAndOwner(Long id, UUID ownerId);

    @Query("select r.id from RecurringTransaction r where r.active = true and r.nextDate <= :date")
    List<Long> findDueIds(LocalDate date);

    @Query("select r.id from RecurringTransaction r where r.owner.id = :ownerId and r.active = true and r.nextDate <= :date")
    List<Long> findDueIdsByOwner(UUID ownerId, LocalDate date);

    @Modifying(flushAutomatically = true)
    @Query("delete from RecurringTransaction r where r.account.id = :accountId or r.targetAccount.id = :accountId")
    void deleteByAccountId(Long accountId);

    @Modifying(flushAutomatically = true)
    @Query("delete from RecurringTransaction r where r.owner.id = :ownerId")
    void deleteByOwnerId(UUID ownerId);

    @Modifying(flushAutomatically = true)
    @Query("update RecurringTransaction r set r.category = null where r.category.id in :categoryIds")
    void clearCategory(List<Long> categoryIds);

    @Modifying(flushAutomatically = true)
    @Query("update RecurringTransaction r set r.category = :replacement where r.category.id in :categoryIds")
    void replaceCategory(List<Long> categoryIds, Category replacement);
}
