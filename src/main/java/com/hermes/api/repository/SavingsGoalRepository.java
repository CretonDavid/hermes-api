package com.hermes.api.repository;

import com.hermes.api.model.SavingsGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, Long> {

    @Query("""
            select g from SavingsGoal g join fetch g.account a join fetch a.currency
            where g.owner.id = :ownerId order by g.startDate, g.id
            """)
    List<SavingsGoal> findAllByOwner(UUID ownerId);

    @Query("select g from SavingsGoal g join fetch g.account a join fetch a.currency where g.id = :id and g.owner.id = :ownerId")
    Optional<SavingsGoal> findByIdAndOwner(Long id, UUID ownerId);

    @Query("select g from SavingsGoal g where g.account.id = :accountId order by g.startDate, g.id")
    List<SavingsGoal> findByAccountId(Long accountId);

    // Les affectations doivent être supprimées avant, avec GoalAllocationRepository
    @Modifying(flushAutomatically = true)
    @Query("delete from SavingsGoal g where g.account.id = :accountId")
    void deleteByAccountId(Long accountId);

    @Modifying(flushAutomatically = true)
    @Query("delete from SavingsGoal g where g.owner.id = :ownerId")
    void deleteByOwnerId(UUID ownerId);
}
