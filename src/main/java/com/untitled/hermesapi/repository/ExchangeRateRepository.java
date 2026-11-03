package com.untitled.hermesapi.repository;

import com.untitled.hermesapi.model.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    @Query("""
            select r from ExchangeRate r
            join fetch r.baseCurrency join fetch r.quoteCurrency
            where r.owner.id = :ownerId
            order by r.date desc, r.id desc
            """)
    List<ExchangeRate> findAllByOwner(UUID ownerId);

    @Query("""
            select r from ExchangeRate r
            join fetch r.baseCurrency join fetch r.quoteCurrency
            where r.owner.id = :ownerId and (r.baseCurrency.id = :currencyId or r.quoteCurrency.id = :currencyId)
            order by r.date desc, r.id desc
            """)
    List<ExchangeRate> findAllByOwnerAndCurrency(UUID ownerId, Long currencyId);

    @Query("""
            select r from ExchangeRate r
            join fetch r.baseCurrency join fetch r.quoteCurrency
            where r.id = :id and r.owner.id = :ownerId
            """)
    Optional<ExchangeRate> findByIdAndOwner(Long id, UUID ownerId);

    @Query("""
            select r from ExchangeRate r
            where r.owner.id = :ownerId and r.baseCurrency.id = :baseId and r.quoteCurrency.id = :quoteId and r.date = :date
            """)
    Optional<ExchangeRate> findPair(UUID ownerId, Long baseId, Long quoteId, LocalDate date);

    @Modifying(flushAutomatically = true)
    @Query("delete from ExchangeRate r where r.owner.id = :ownerId")
    void deleteByOwnerId(UUID ownerId);

    @Modifying(flushAutomatically = true)
    @Query("delete from ExchangeRate r where r.baseCurrency.id = :currencyId or r.quoteCurrency.id = :currencyId")
    void deleteByCurrencyId(Long currencyId);
}
