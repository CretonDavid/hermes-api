package com.untitled.hermesapi.repository;

import com.untitled.hermesapi.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, Long> {

    @Query("""
            select a from Account a
            join fetch a.accountType join fetch a.currency
            where a.owner.id = :ownerId
            order by a.closed asc, a.name asc
            """)
    List<Account> findAllByOwner(UUID ownerId);

    @Query("""
            select a from Account a
            join fetch a.accountType join fetch a.currency
            where a.id = :id and a.owner.id = :ownerId
            """)
    Optional<Account> findByIdAndOwner(Long id, UUID ownerId);

    boolean existsByAccountType_Id(Long accountTypeId);

    boolean existsByCurrency_Id(Long currencyId);

    @Modifying(flushAutomatically = true)
    @Query("delete from Account a where a.owner.id = :ownerId")
    void deleteByOwnerId(UUID ownerId);
}
