package com.hermes.api.repository;

import com.hermes.api.model.ReimbursementPayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface ReimbursementPayerRepository extends JpaRepository<ReimbursementPayer, Long> {

    @Modifying(flushAutomatically = true)
    @Query("delete from ReimbursementPayer p where p.recurringTransaction.id in "
            + "(select r.id from RecurringTransaction r where r.account.id = :accountId or r.targetAccount.id = :accountId)")
    void deleteByAccountId(Long accountId);

    @Modifying(flushAutomatically = true)
    @Query("delete from ReimbursementPayer p where p.recurringTransaction.id in "
            + "(select r.id from RecurringTransaction r where r.owner.id = :ownerId)")
    void deleteByOwnerId(UUID ownerId);
}
