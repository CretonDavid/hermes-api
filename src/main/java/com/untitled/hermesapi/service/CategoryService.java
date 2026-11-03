package com.untitled.hermesapi.service;

import com.untitled.hermesapi.dto.category.CategoryRequest;
import com.untitled.hermesapi.dto.category.CategoryResponse;
import com.untitled.hermesapi.exception.BadRequestException;
import com.untitled.hermesapi.exception.ResourceNotFoundException;
import com.untitled.hermesapi.mapper.CategoryMapper;
import com.untitled.hermesapi.model.Category;
import com.untitled.hermesapi.model.User;
import com.untitled.hermesapi.repository.CategoryRepository;
import com.untitled.hermesapi.repository.RecurringTransactionRepository;
import com.untitled.hermesapi.repository.TransactionRepository;
import com.untitled.hermesapi.utils.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final RecurringTransactionRepository recurringTransactionRepository;
    private final CategoryMapper categoryMapper;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        return categoryRepository.findAllByOwner(currentUser.id()).stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        Category category = Category.builder().owner(currentUser.reference()).build();
        apply(category, request);
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = find(id);
        apply(category, request);

        // Une catégorie et ses sous-catégories partagent toujours le même type
        for (Category descendant : descendants(category)) {
            descendant.setType(category.getType());
        }
        return categoryMapper.toResponse(category);
    }

    /**
     * Supprime une catégorie. Ses sous-catégories remontent d'un niveau ; ses opérations sont
     * déplacées vers la catégorie de remplacement si elle est fournie, sinon elles deviennent
     * non catégorisées.
     */
    @Transactional
    public void delete(Long id, Long replacementId) {
        Category category = find(id);

        Category replacement = null;
        if (replacementId != null) {
            if (replacementId.equals(id)) {
                throw new BadRequestException("La catégorie de remplacement doit être différente");
            }
            replacement = find(replacementId);
        }

        for (Category child : categoryRepository.findByParent_Id(id)) {
            child.setParent(category.getParent());
        }

        List<Long> ids = List.of(id);
        if (replacement != null) {
            transactionRepository.replaceCategory(ids, replacement);
            recurringTransactionRepository.replaceCategory(ids, replacement);
        } else {
            transactionRepository.clearCategory(ids);
            recurringTransactionRepository.clearCategory(ids);
        }
        categoryRepository.delete(category);
        log.info("Category {} deleted", id);
    }

    @Transactional
    public void createDefaultCategories(User owner) {
        List<Category> categories = new ArrayList<>();
        for (DefaultCategories.Definition definition : DefaultCategories.ALL) {
            Category parent = Category.builder()
                    .owner(owner)
                    .name(definition.name())
                    .type(definition.type())
                    .color(definition.color())
                    .icon(definition.icon())
                    .build();
            categories.add(parent);
            for (String childName : definition.children()) {
                categories.add(Category.builder()
                        .owner(owner)
                        .name(childName)
                        .type(definition.type())
                        .color(definition.color())
                        .parent(parent)
                        .build());
            }
        }
        categoryRepository.saveAll(categories);
    }

    public Category find(Long id) {
        return categoryRepository.findByIdAndOwner_Id(id, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie introuvable"));
    }

    /**
     * Identifiants de la catégorie et de toutes ses sous-catégories.
     */
    @Transactional(readOnly = true)
    public Set<Long> withDescendants(Collection<Long> categoryIds) {
        Map<Long, List<Long>> children = new HashMap<>();
        for (Category category : categoryRepository.findAllByOwner(currentUser.id())) {
            if (category.getParent() != null) {
                children.computeIfAbsent(category.getParent().getId(), k -> new ArrayList<>()).add(category.getId());
            }
        }
        Set<Long> result = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>(categoryIds);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (result.add(current)) {
                queue.addAll(children.getOrDefault(current, List.of()));
            }
        }
        return result;
    }

    private void apply(Category category, CategoryRequest request) {
        category.setName(request.name().trim());
        category.setColor(request.color());
        category.setIcon(AccountService.blankToNull(request.icon()));

        if (request.parentId() == null) {
            category.setParent(null);
            category.setType(request.type());
            return;
        }

        Category parent = find(request.parentId());
        if (category.getId() != null) {
            if (parent.getId().equals(category.getId()) || descendants(category).contains(parent)) {
                throw new BadRequestException("Une catégorie ne peut pas être rangée dans l'une de ses sous-catégories");
            }
        }
        category.setParent(parent);
        category.setType(parent.getType());
    }

    private List<Category> descendants(Category root) {
        List<Category> all = categoryRepository.findAllByOwner(currentUser.id());
        List<Category> result = new ArrayList<>();
        Deque<Long> queue = new ArrayDeque<>(List.of(root.getId()));
        while (!queue.isEmpty()) {
            Long parentId = queue.poll();
            for (Category candidate : all) {
                if (candidate.getParent() != null && candidate.getParent().getId().equals(parentId)
                        && !result.contains(candidate)) {
                    result.add(candidate);
                    queue.add(candidate.getId());
                }
            }
        }
        return result;
    }
}
