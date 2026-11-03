package com.untitled.hermesapi.repository;

import com.untitled.hermesapi.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    @Query("select c from Category c left join fetch c.parent where c.owner.id = :ownerId order by c.name")
    List<Category> findAllByOwner(UUID ownerId);

    Optional<Category> findByIdAndOwner_Id(Long id, UUID ownerId);

    List<Category> findByParent_Id(Long parentId);

    @Modifying(flushAutomatically = true)
    @Query("update Category c set c.parent = null where c.owner.id = :ownerId")
    void clearParentsByOwnerId(UUID ownerId);

    @Modifying(flushAutomatically = true)
    @Query("delete from Category c where c.owner.id = :ownerId")
    void deleteByOwnerId(UUID ownerId);
}
