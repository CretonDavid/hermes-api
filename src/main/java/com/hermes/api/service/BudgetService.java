package com.hermes.api.service;

import com.hermes.api.dto.budget.*;
import com.hermes.api.exception.BadRequestException;
import com.hermes.api.exception.ResourceNotFoundException;
import com.hermes.api.mapper.CategoryMapper;
import com.hermes.api.model.BudgetAmount;
import com.hermes.api.model.BudgetEnvelope;
import com.hermes.api.model.Category;
import com.hermes.api.model.enums.CategoryType;
import com.hermes.api.repository.BudgetEnvelopeRepository;
import com.hermes.api.repository.CategoryRepository;
import com.hermes.api.utils.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Budget mensuel par enveloppes : une enveloppe par catégorie de dépense, qui couvre aussi ses
 * sous-catégories. Les dépenses sont celles des statistiques (tous comptes, remboursements
 * déduits, virements exclus), dans la devise de référence.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetEnvelopeRepository envelopeRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryService categoryService;
    private final StatsService statsService;
    private final CategoryMapper categoryMapper;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public BudgetResponse month(YearMonth month) {
        UUID userId = currentUser.id();
        LocalDate start = month.atDay(1);
        Map<Long, Category> categories = categoryRepository.findAllByOwner(userId).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));

        // Enveloppes existant ce mois-ci, par catégorie
        Map<Long, BudgetEnvelope> byCategory = new HashMap<>();
        for (BudgetEnvelope envelope : envelopeRepository.findAllByOwner(userId)) {
            if (envelope.amountFor(start).isPresent()) {
                byCategory.put(envelope.getCategory().getId(), envelope);
            }
        }

        // Chaque dépense va à l'enveloppe la plus proche en remontant les catégories parentes
        StatsService.CategoryExpenses expenses = statsService.expensesByCategory(start, month.atEndOfMonth());
        Map<Long, BigDecimal> spent = new HashMap<>();
        BigDecimal unbudgeted = expenses.uncategorized();
        for (Map.Entry<Long, BigDecimal> entry : expenses.byCategory().entrySet()) {
            Long envelopeCategory = nearestEnvelope(entry.getKey(), categories, byCategory.keySet());
            if (envelopeCategory == null) {
                unbudgeted = unbudgeted.add(entry.getValue());
            } else {
                spent.merge(envelopeCategory, entry.getValue(), BigDecimal::add);
            }
        }

        List<EnvelopeResponse> envelopes = byCategory.values().stream()
                .map(envelope -> toResponse(envelope, start,
                        spent.getOrDefault(envelope.getCategory().getId(), BigDecimal.ZERO)))
                .sorted(Comparator.comparing((EnvelopeResponse e) -> e.category().name(), String.CASE_INSENSITIVE_ORDER))
                .toList();
        BigDecimal budgeted = sum(envelopes.stream().map(EnvelopeResponse::amount));
        BigDecimal totalSpent = sum(envelopes.stream().map(EnvelopeResponse::spent));
        return new BudgetResponse(month, expenses.currencyCode(), expenses.missingRates(),
                budgeted, totalSpent, round(unbudgeted), envelopes);
    }

    @Transactional
    public void create(EnvelopeRequest request) {
        Category category = categoryService.find(request.categoryId());
        if (category.getType() != CategoryType.EXPENSE) {
            throw new BadRequestException("Un budget ne porte que sur une catégorie de dépense");
        }
        ensureNoOverlap(category);

        BudgetEnvelope envelope = BudgetEnvelope.builder()
                .owner(currentUser.reference())
                .category(category)
                .build();
        envelope.getAmounts().add(amount(envelope, request.month(), request.amount(), false));
        envelopeRepository.save(envelope);
    }

    /**
     * Nouveau montant pour ce mois seulement, ou à partir de ce mois : il remplace alors les
     * montants habituels fixés pour les mois suivants, mais garde leurs exceptions propres.
     */
    @Transactional
    public void updateAmount(Long id, EnvelopeAmountRequest request) {
        BudgetEnvelope envelope = find(id);
        LocalDate month = request.month().atDay(1);
        List<BudgetAmount> amounts = envelope.getAmounts();
        if (request.monthOnly()) {
            amounts.removeIf(a -> a.isMonthOnly() && a.getMonth().equals(month));
        } else {
            amounts.removeIf(a -> (!a.isMonthOnly() && !a.getMonth().isBefore(month))
                    || (a.isMonthOnly() && a.getMonth().equals(month)));
        }
        amounts.add(amount(envelope, request.month(), request.amount(), request.monthOnly()));
    }

    // Le mois revient au montant habituel
    @Transactional
    public void clearMonthAmount(Long id, YearMonth month) {
        BudgetEnvelope envelope = find(id);
        LocalDate start = month.atDay(1);
        if (envelope.usualAmount(start).isEmpty()) {
            throw new BadRequestException("Ce mois n'a pas de montant habituel : supprimez plutôt l'enveloppe");
        }
        envelope.getAmounts().removeIf(a -> a.isMonthOnly() && a.getMonth().equals(start));
    }

    @Transactional
    public void delete(Long id) {
        envelopeRepository.delete(find(id));
        log.info("Budget envelope {} deleted", id);
    }

    private BudgetEnvelope find(Long id) {
        return envelopeRepository.findByIdAndOwner(id, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("Enveloppe introuvable"));
    }

    // Une catégorie et l'une de ses parentes ou sous-catégories ne peuvent pas avoir chacune une enveloppe
    private void ensureNoOverlap(Category category) {
        Map<Long, Category> categories = categoryRepository.findAllByOwner(currentUser.id()).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        Map<Long, String> envelopes = envelopeRepository.findAllByOwner(currentUser.id()).stream()
                .collect(Collectors.toMap(e -> e.getCategory().getId(), e -> e.getCategory().getName()));
        if (envelopes.containsKey(category.getId())) {
            throw new BadRequestException("La catégorie " + category.getName() + " a déjà une enveloppe");
        }
        Long ancestor = nearestEnvelope(category.getId(), categories, envelopes.keySet());
        if (ancestor != null) {
            throw new BadRequestException("L'enveloppe " + envelopes.get(ancestor)
                    + " couvre déjà " + category.getName() + " : modifiez-la plutôt");
        }
        for (Long other : envelopes.keySet()) {
            if (path(other, categories).contains(category.getId())) {
                throw new BadRequestException("La sous-catégorie " + envelopes.get(other)
                        + " a déjà une enveloppe : supprimez-la d'abord pour budgéter " + category.getName());
            }
        }
    }

    private static Long nearestEnvelope(Long categoryId, Map<Long, Category> categories, Set<Long> envelopes) {
        for (Long id : path(categoryId, categories)) {
            if (envelopes.contains(id)) {
                return id;
            }
        }
        return null;
    }

    // La catégorie puis ses parentes, jusqu'à la catégorie principale
    private static List<Long> path(Long categoryId, Map<Long, Category> categories) {
        List<Long> path = new ArrayList<>();
        Long current = categoryId;
        while (current != null && !path.contains(current)) {
            path.add(current);
            Category category = categories.get(current);
            current = category == null || category.getParent() == null ? null : category.getParent().getId();
        }
        return path;
    }

    private EnvelopeResponse toResponse(BudgetEnvelope envelope, LocalDate month, BigDecimal spent) {
        BigDecimal amount = envelope.amountFor(month).orElseThrow().getAmount();
        BigDecimal usual = envelope.usualAmount(month).map(BudgetAmount::getAmount).orElse(null);
        BigDecimal roundedSpent = round(spent);
        return new EnvelopeResponse(envelope.getId(), categoryMapper.toSummary(envelope.getCategory()),
                amount, usual, envelope.monthOnlyAmount(month).isPresent(), roundedSpent,
                amount.subtract(roundedSpent));
    }

    private static BudgetAmount amount(BudgetEnvelope envelope, YearMonth month, BigDecimal value, boolean monthOnly) {
        return BudgetAmount.builder()
                .envelope(envelope)
                .month(month.atDay(1))
                .amount(value)
                .monthOnly(monthOnly)
                .build();
    }

    private static BigDecimal sum(java.util.stream.Stream<BigDecimal> values) {
        return values.reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_EVEN);
    }
}
