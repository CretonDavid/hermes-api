package com.hermes.api.repository;

import com.hermes.api.model.BudgetEnvelope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetEnvelopeRepository extends JpaRepository<BudgetEnvelope, Long> {

    @Query("""
            select distinct e from BudgetEnvelope e
            join fetch e.category c left join fetch e.amounts
            where e.owner.id = :ownerId
            """)
    List<BudgetEnvelope> findAllByOwner(UUID ownerId);

    @Query("select e from BudgetEnvelope e join fetch e.category where e.id = :id and e.owner.id = :ownerId")
    Optional<BudgetEnvelope> findByIdAndOwner(Long id, UUID ownerId);

    List<BudgetEnvelope> findByCategory_Id(Long categoryId);

    @Modifying(flushAutomatically = true)
    @Query("delete from BudgetAmount a where a.envelope.id in (select e.id from BudgetEnvelope e where e.owner.id = :ownerId)")
    void deleteAmountsByOwnerId(UUID ownerId);

    @Modifying(flushAutomatically = true)
    @Query("delete from BudgetEnvelope e where e.owner.id = :ownerId")
    void deleteByOwnerId(UUID ownerId);
}
