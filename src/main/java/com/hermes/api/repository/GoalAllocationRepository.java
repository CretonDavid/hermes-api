package com.hermes.api.repository;

import com.hermes.api.model.GoalAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface GoalAllocationRepository extends JpaRepository<GoalAllocation, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from GoalAllocation a where a.goal.id = :goalId")
    void deleteByGoalId(Long goalId);

    // A appeler avant la suppression en masse des opérations ou des budgets spéciaux du compte
    @Modifying(flushAutomatically = true)
    @Query("delete from GoalAllocation a where a.transaction.id in "
            + "(select t.id from Transaction t where t.account.id = :accountId) "
            + "or a.goal.id in (select g.id from SavingsGoal g where g.account.id = :accountId)")
    void deleteByAccountId(Long accountId);

    @Modifying(flushAutomatically = true)
    @Query("delete from GoalAllocation a where a.goal.id in (select g.id from SavingsGoal g where g.owner.id = :ownerId)")
    void deleteByOwnerId(UUID ownerId);
}
