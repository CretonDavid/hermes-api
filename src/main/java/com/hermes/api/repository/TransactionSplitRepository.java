package com.hermes.api.repository;

import com.hermes.api.model.Category;
import com.hermes.api.model.TransactionSplit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface TransactionSplitRepository extends JpaRepository<TransactionSplit, Long> {

    @Modifying(flushAutomatically = true)
    @Query("update TransactionSplit s set s.category = null where s.category.id in :categoryIds")
    void clearCategory(List<Long> categoryIds);

    @Modifying(flushAutomatically = true)
    @Query("update TransactionSplit s set s.category = :replacement where s.category.id in :categoryIds")
    void replaceCategory(List<Long> categoryIds, Category replacement);

    // A appeler avant la suppression en masse des opérations correspondantes
    @Modifying(flushAutomatically = true)
    @Query("delete from TransactionSplit s where s.transaction.id in "
            + "(select t.id from Transaction t where t.account.id = :accountId)")
    void deleteByAccountId(Long accountId);

    @Modifying(flushAutomatically = true)
    @Query("delete from TransactionSplit s where s.transaction.id in "
            + "(select t.id from Transaction t where t.account.owner.id = :ownerId)")
    void deleteByOwnerId(UUID ownerId);
}
