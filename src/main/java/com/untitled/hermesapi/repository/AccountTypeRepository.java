package com.untitled.hermesapi.repository;

import com.untitled.hermesapi.model.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountTypeRepository extends JpaRepository<AccountType, Long> {

    List<AccountType> findByOwner_IdOrderByNameAsc(UUID ownerId);

    Optional<AccountType> findByIdAndOwner_Id(Long id, UUID ownerId);

    boolean existsByOwner_IdAndNameIgnoreCase(UUID ownerId, String name);

    @Modifying(flushAutomatically = true)
    @Query("delete from AccountType t where t.owner.id = :ownerId")
    void deleteByOwnerId(UUID ownerId);
}
