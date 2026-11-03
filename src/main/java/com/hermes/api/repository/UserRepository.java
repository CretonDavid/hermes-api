package com.hermes.api.repository;

import com.hermes.api.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByLookupKey(String lookupKey);

    Optional<User> findByLookupKey(String lookupKey);

    @EntityGraph(attributePaths = "referenceCurrency")
    @Query("select u from User u where u.id = :id")
    Optional<User> findWithCurrencyById(UUID id);
}
