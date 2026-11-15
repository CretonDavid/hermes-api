package com.hermes.api.repository;

import com.hermes.api.model.GoalAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface GoalAdjustmentRepository extends JpaRepository<GoalAdjustment, Long> {

    @Query("select a from GoalAdjustment a join fetch a.goal where a.goal.account.id = :accountId")
    List<GoalAdjustment> findByAccountId(Long accountId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from GoalAdjustment a where a.goal.id = :goalId")
    void deleteByGoalId(Long goalId);

    // A appeler avant la suppression en masse des budgets spéciaux du compte
    @Modifying(flushAutomatically = true)
    @Query("delete from GoalAdjustment a where a.goal.id in (select g.id from SavingsGoal g where g.account.id = :accountId)")
    void deleteByAccountId(Long accountId);

    @Modifying(flushAutomatically = true)
    @Query("delete from GoalAdjustment a where a.goal.id in (select g.id from SavingsGoal g where g.owner.id = :ownerId)")
    void deleteByOwnerId(UUID ownerId);
}
