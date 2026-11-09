package com.hermes.api.repository;

import com.hermes.api.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Modifying(flushAutomatically = true)
    @Query("delete from RefreshToken t where t.user.id = :userId")
    int deleteByUserId(UUID userId);

    @Modifying(flushAutomatically = true)
    @Query("delete from RefreshToken t where t.expiresAt < :now")
    int deleteExpired(Instant now);
}
