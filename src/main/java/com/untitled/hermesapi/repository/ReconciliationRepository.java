package com.untitled.hermesapi.repository;

import com.untitled.hermesapi.model.Reconciliation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReconciliationRepository extends JpaRepository<Reconciliation, Long> {

    List<Reconciliation> findByAccount_IdOrderByStatementDateDescIdDesc(Long accountId);

    Optional<Reconciliation> findFirstByAccount_IdOrderByStatementDateDescIdDesc(Long accountId);

    @Modifying(flushAutomatically = true)
    @Query("delete from Reconciliation r where r.account.id = :accountId")
    void deleteByAccountId(Long accountId);

    @Modifying(flushAutomatically = true)
    @Query("delete from Reconciliation r where r.account.id in (select a.id from Account a where a.owner.id = :ownerId)")
    void deleteByOwnerId(UUID ownerId);
}
