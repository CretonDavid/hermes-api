package com.hermes.api.service;

import com.hermes.api.dto.goal.*;
import com.hermes.api.exception.BadRequestException;
import com.hermes.api.exception.ResourceNotFoundException;
import com.hermes.api.model.*;
import com.hermes.api.model.enums.TransactionDirection;
import com.hermes.api.repository.GoalAllocationRepository;
import com.hermes.api.repository.SavingsGoalRepository;
import com.hermes.api.repository.TransactionRepository;
import com.hermes.api.utils.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Budgets spéciaux : objectifs d'épargne mis de côté sur un compte bancaire.
 * <p>
 * Le montant de chaque budget se calcule en rejouant l'historique du compte jusqu'à aujourd'hui,
 * dans l'ordre chronologique. Dans une journée, l'argent qui entre passe d'abord (montants de
 * départ des budgets créés ce jour-là, puis crédits, dont les affectés alimentent leurs budgets), puis
 * les budgets utilisés ce jour-là sont libérés, enfin les retraits sont passés : acheter la moto avec
 * l'argent du livret le jour où son budget est utilisé n'entame pas les autres. Après chaque
 * mouvement, si le solde du compte passe sous le total de ses budgets, ils sont réduits au prorata
 * pour tenir dans le solde : une réduction est définitive, un dépôt ultérieur non affecté ne
 * remplit pas le budget.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoalService {

    private final SavingsGoalRepository goalRepository;
    private final GoalAllocationRepository allocationRepository;
    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final CurrentUser currentUser;

    // ---------- Consultation ----------

    @Transactional(readOnly = true)
    public List<GoalResponse> findAll() {
        List<SavingsGoal> goals = goalRepository.findAllByOwner(currentUser.id());
        Map<Long, AccountState> states = new HashMap<>();
        return goals.stream()
                .map(goal -> toResponse(goal, states.computeIfAbsent(goal.getAccount().getId(),
                        id -> replay(goal.getAccount(), null))))
                .toList();
    }

    // ---------- Gestion ----------

    @Transactional
    public GoalResponse create(GoalRequest request) {
        Account account = accountService.find(request.accountId());
        TransactionService.ensureOpen(account);
        BigDecimal initial = request.initialAmount() == null ? BigDecimal.ZERO : request.initialAmount();
        if (initial.compareTo(request.targetAmount()) > 0) {
            throw new BadRequestException("Le montant de départ dépasse l'objectif");
        }
        AccountState state = replay(account, null);
        BigDecimal free = state.balance().max(BigDecimal.ZERO).subtract(state.total());
        if (initial.compareTo(free) > 0) {
            throw new BadRequestException("Il n'y a que " + free.max(BigDecimal.ZERO).toPlainString() + " "
                    + account.getCurrency().getCode() + " de libres sur " + account.getName()
                    + " (solde moins les autres budgets spéciaux)");
        }

        SavingsGoal goal = goalRepository.save(SavingsGoal.builder()
                .owner(currentUser.reference())
                .account(account)
                .name(request.name().trim())
                .targetAmount(request.targetAmount())
                .initialAmount(initial)
                .startDate(LocalDate.now())
                .createdAt(LocalDateTime.now())
                .build());
        return toResponse(goal, replay(account, null));
    }

    @Transactional
    public GoalResponse update(Long id, GoalUpdateRequest request) {
        SavingsGoal goal = find(id);
        goal.setName(request.name().trim());
        goal.setTargetAmount(request.targetAmount());
        return toResponse(goal, replay(goal.getAccount(), null));
    }

    @Transactional
    public GoalResponse use(Long id, GoalUseRequest request) {
        SavingsGoal goal = find(id);
        if (goal.isUsed()) {
            throw new BadRequestException("Ce budget spécial est déjà utilisé");
        }
        if (request.date().isBefore(goal.getStartDate())) {
            throw new BadRequestException("La date doit être postérieure à la création du budget spécial");
        }
        goal.setUsedDate(request.date());
        return toResponse(goal, replay(goal.getAccount(), null));
    }

    // Le budget revient parmi les budgets en cours
    @Transactional
    public GoalResponse restore(Long id) {
        SavingsGoal goal = find(id);
        goal.setUsedDate(null);
        return toResponse(goal, replay(goal.getAccount(), null));
    }

    // L'argent reste sur le compte, seules les affectations disparaissent
    @Transactional
    public void delete(Long id) {
        SavingsGoal goal = find(id);
        allocationRepository.deleteByGoalId(goal.getId());
        goalRepository.delete(goalRepository.getReferenceById(goal.getId()));
        log.info("Savings goal {} deleted", id);
    }

    // ---------- Affectation des crédits ----------

    /**
     * Remplace les affectations d'une opération. Seul un crédit peut alimenter les budgets spéciaux
     * de son compte, sans dépasser son montant ni ce qu'il manque à chaque budget.
     */
    public void applyAllocations(Transaction transaction, List<GoalAllocationRequest> requests) {
        transaction.getGoalAllocations().clear();
        if (requests == null || requests.isEmpty()) {
            return;
        }
        if (transaction.getDirection() != TransactionDirection.CREDIT) {
            throw new BadRequestException("Seul un crédit peut alimenter un budget spécial");
        }
        BigDecimal sum = requests.stream().map(GoalAllocationRequest::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(transaction.getAmount()) > 0) {
            throw new BadRequestException("Les montants affectés aux budgets spéciaux dépassent le montant crédité");
        }

        Account account = transaction.getAccount();
        // L'opération elle-même ne compte pas : ses anciennes affectations sont remplacées
        AccountState state = replay(account, transaction.getId());
        Set<Long> seen = new HashSet<>();
        for (GoalAllocationRequest request : requests) {
            SavingsGoal goal = find(request.goalId());
            if (!seen.add(goal.getId())) {
                throw new BadRequestException("Le budget spécial " + goal.getName() + " apparaît deux fois");
            }
            if (!goal.getAccount().getId().equals(account.getId())) {
                throw new BadRequestException("Le budget spécial " + goal.getName() + " est lié à un autre compte bancaire");
            }
            if (goal.isUsed()) {
                throw new BadRequestException("Le budget spécial " + goal.getName() + " est déjà utilisé");
            }
            BigDecimal missing = goal.getTargetAmount().subtract(state.saved(goal.getId()));
            if (missing.signum() <= 0) {
                throw new BadRequestException("Le budget spécial " + goal.getName()
                        + " a atteint son objectif : il est verrouillé");
            }
            if (request.amount().compareTo(missing) > 0) {
                throw new BadRequestException("Il ne manque que " + missing.toPlainString() + " "
                        + account.getCurrency().getCode() + " pour atteindre l'objectif de " + goal.getName());
            }
            transaction.getGoalAllocations().add(GoalAllocation.builder()
                    .transaction(transaction)
                    .goal(goal)
                    .amount(request.amount())
                    .build());
        }
    }

    // ---------- Calcul ----------

    private record GoalState(BigDecimal saved, BigDecimal added, BigDecimal usedAmount) {
    }

    /**
     * État d'un compte aujourd'hui : solde et montant de chaque budget spécial.
     */
    private record AccountState(BigDecimal balance, Map<Long, BigDecimal> active, Map<Long, BigDecimal> added,
                                Map<Long, BigDecimal> used) {

        BigDecimal saved(Long goalId) {
            return active.getOrDefault(goalId, used.getOrDefault(goalId, BigDecimal.ZERO));
        }

        BigDecimal total() {
            return active.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    private enum Kind {START, CREDIT, RELEASE, DEBIT}

    private record Event(LocalDate date, Kind kind, long order, SavingsGoal goal, Transaction transaction) {
    }

    /**
     * Rejoue l'historique du compte jusqu'à aujourd'hui.
     *
     * @param ignoredTransactionId opération dont les affectations ne comptent pas (en cours de modification)
     */
    private AccountState replay(Account account, Long ignoredTransactionId) {
        LocalDate today = LocalDate.now();
        List<Event> events = new ArrayList<>();
        for (SavingsGoal goal : goalRepository.findByAccountId(account.getId())) {
            events.add(new Event(goal.getStartDate(), Kind.START, goal.getId(), goal, null));
            if (goal.getUsedDate() != null && !goal.getUsedDate().isAfter(today)) {
                events.add(new Event(goal.getUsedDate(), Kind.RELEASE, goal.getId(), goal, null));
            }
        }
        for (Transaction t : transactionRepository.findForGoals(account.getId(), today)) {
            Kind kind = t.getDirection() == TransactionDirection.CREDIT ? Kind.CREDIT : Kind.DEBIT;
            events.add(new Event(t.getOperationDate(), kind, t.getId(), null, t));
        }
        // Dans une journée : montants de départ, crédits, libérations, puis retraits
        events.sort(Comparator.comparing(Event::date).thenComparing(Event::kind).thenComparingLong(Event::order));

        BigDecimal balance = account.getInitialBalance();
        Map<Long, BigDecimal> active = new LinkedHashMap<>();
        Map<Long, BigDecimal> added = new HashMap<>();
        Map<Long, BigDecimal> used = new HashMap<>();
        Set<Long> released = new HashSet<>();
        for (Event event : events) {
            switch (event.kind()) {
                case RELEASE -> {
                    released.add(event.goal().getId());
                    used.put(event.goal().getId(), active.getOrDefault(event.goal().getId(), BigDecimal.ZERO));
                    active.remove(event.goal().getId());
                }
                case START -> {
                    if (!released.contains(event.goal().getId())) {
                        add(active, added, event.goal().getId(), event.goal().getInitialAmount());
                    }
                }
                case CREDIT, DEBIT -> {
                    Transaction t = event.transaction();
                    balance = balance.add(t.signedAmount());
                    if (t.getDirection() == TransactionDirection.CREDIT && !t.getId().equals(ignoredTransactionId)) {
                        for (GoalAllocation allocation : t.getGoalAllocations()) {
                            if (!released.contains(allocation.getGoal().getId())) {
                                add(active, added, allocation.getGoal().getId(), allocation.getAmount());
                            }
                        }
                    }
                }
            }
            fitInto(active, balance);
        }
        return new AccountState(balance, active, added, used);
    }

    private static void add(Map<Long, BigDecimal> active, Map<Long, BigDecimal> added, Long goalId, BigDecimal amount) {
        active.merge(goalId, amount, BigDecimal::add);
        added.merge(goalId, amount, BigDecimal::add);
    }

    // Réduit les budgets au prorata pour que leur total ne dépasse pas le solde
    private static void fitInto(Map<Long, BigDecimal> active, BigDecimal balance) {
        BigDecimal total = active.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal available = balance.max(BigDecimal.ZERO);
        if (total.compareTo(available) <= 0) {
            return;
        }
        // Arrondi par défaut : le total réduit ne dépasse jamais le solde
        active.replaceAll((id, amount) -> amount.multiply(available).divide(total, 2, RoundingMode.DOWN));
    }

    private GoalResponse toResponse(SavingsGoal goal, AccountState state) {
        Account account = goal.getAccount();
        BigDecimal saved = state.saved(goal.getId());
        BigDecimal remaining = goal.getTargetAmount().subtract(saved).max(BigDecimal.ZERO);
        BigDecimal withdrawn = state.added().getOrDefault(goal.getId(), BigDecimal.ZERO).subtract(saved).max(BigDecimal.ZERO);
        return new GoalResponse(goal.getId(), goal.getName(), account.getId(), account.getName(),
                account.getCurrency().getCode(), goal.getTargetAmount(), goal.getInitialAmount(), saved, remaining,
                remaining.signum() == 0, withdrawn, goal.getStartDate(), goal.getUsedDate());
    }

    private SavingsGoal find(Long id) {
        return goalRepository.findByIdAndOwner(id, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("Budget spécial introuvable"));
    }
}
