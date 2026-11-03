package com.untitled.hermesapi.repository;

import com.untitled.hermesapi.model.Currency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CurrencyRepository extends JpaRepository<Currency, Long> {

    Optional<Currency> findByCodeAndOwnerIsNull(String code);

    List<Currency> findByOwnerIsNull();

    // Devises visibles par un utilisateur : devises par défaut + ses propres devises
    @Query("select c from Currency c where c.owner is null or c.owner.id = :ownerId order by c.code")
    List<Currency> findAccessible(UUID ownerId);

    @Query("select c from Currency c where c.id = :id and (c.owner is null or c.owner.id = :ownerId)")
    Optional<Currency> findAccessibleById(Long id, UUID ownerId);

    @Query("select c from Currency c where upper(c.code) = upper(:code) and (c.owner is null or c.owner.id = :ownerId)")
    List<Currency> findAccessibleByCode(String code, UUID ownerId);

    @Modifying(flushAutomatically = true)
    @Query("delete from Currency c where c.owner.id = :ownerId")
    void deleteByOwnerId(UUID ownerId);
}
