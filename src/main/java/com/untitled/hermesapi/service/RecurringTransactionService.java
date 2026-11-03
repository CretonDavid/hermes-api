package com.untitled.hermesapi.service;

import com.untitled.hermesapi.dto.recurring.RecurringTransactionRequest;
import com.untitled.hermesapi.dto.recurring.RecurringTransactionResponse;
import com.untitled.hermesapi.dto.recurring.UpcomingOccurrenceResponse;
import com.untitled.hermesapi.exception.BadRequestException;
import com.untitled.hermesapi.exception.ResourceNotFoundException;
import com.untitled.hermesapi.mapper.TransactionMapper;
import com.untitled.hermesapi.model.Account;
import com.untitled.hermesapi.model.RecurringTransaction;
import com.untitled.hermesapi.model.Transaction;
import com.untitled.hermesapi.model.enums.TransactionDirection;
import com.untitled.hermesapi.model.enums.TransactionStatus;
import com.untitled.hermesapi.repository.RecurringTransactionRepository;
import com.untitled.hermesapi.repository.TransactionRepository;
import com.untitled.hermesapi.utils.CurrentUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
public class RecurringTransactionService {

    // Garde-fou contre une génération massive (ex. récurrence quotidienne démarrant il y a des années)
    private static final int MAX_OCCURRENCES_PER_RUN = 1000;
    private static final int MAX_UPCOMING_DAYS = 366;

    private final RecurringTransactionRepository recurringRepository;
    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final TransferService transferService;
    private final TransactionMapper transactionMapper;
    private final CurrentUser currentUser;
    private final TransactionTemplate transactionTemplate;

    public RecurringTransactionService(RecurringTransactionRepository recurringRepository,
                                       TransactionRepository transactionRepository,
                                       AccountService accountService,
                                       CategoryService categoryService,
                                       TransferService transferService,
                                       TransactionMapper transactionMapper,
                                       CurrentUser currentUser,
                                       PlatformTransactionManager transactionManager) {
        this.recurringRepository = recurringRepository;
        this.transactionRepository = transactionRepository;
        this.accountService = accountService;
        this.categoryService = categoryService;
        this.transferService = transferService;
        this.transactionMapper = transactionMapper;
        this.currentUser = currentUser;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public List<RecurringTransactionResponse> findAll() {
        return recurringRepository.findAllByOwner(currentUser.id()).stream()
                .map(transactionMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RecurringTransactionResponse getById(Long id) {
        return transactionMapper.toResponse(find(id));
    }

    @Transactional
    public RecurringTransactionResponse create(RecurringTransactionRequest request) {
        RecurringTransaction recurring = RecurringTransaction.builder()
                .owner(currentUser.reference())
                .createdAt(LocalDateTime.now())
                .build();
        apply(recurring, request);
        schedule(recurring, 0, request.active());

        recurringRepository.save(recurring);
        return transactionMapper.toResponse(recurring);
    }

    @Transactional
    public RecurringTransactionResponse update(Long id, RecurringTransactionRequest request) {
        RecurringTransaction recurring = find(id);
        boolean scheduleChanged = recurring.getFrequency() != request.frequency()
                || recurring.getInterval() != request.interval()
                || !recurring.getStartDate().equals(request.startDate())
                || !Objects.equals(recurring.getEndDate(), request.endDate());
        apply(recurring, request);

        int index = recurring.getOccurrenceCount();
        if (scheduleChanged) {
            // On reprend après la dernière occurrence déjà générée pour ne pas créer de doublons
            LocalDate lastGenerated = transactionRepository.findLastOperationDateForRecurring(id).orElse(null);
            index = 0;
            if (lastGenerated != null) {
                while (index < 100_000
                        && !recurring.getFrequency().occurrence(recurring.getStartDate(), recurring.getInterval(), index).isAfter(lastGenerated)) {
                    index++;
                }
            }
        }
        schedule(recurring, index, request.active());
        return transactionMapper.toResponse(recurring);
    }

    /**
     * Supprime la récurrence. Les opérations déjà générées sont conservées.
     */
    @Transactional
    public void delete(Long id) {
        RecurringTransaction recurring = find(id);
        transactionRepository.detachRecurring(id);
        recurringRepository.delete(recurring);
    }

    /**
     * Prochaines occurrences de toutes les récurrences actives, sur les jours à venir.
     */
    @Transactional(readOnly = true)
    public List<UpcomingOccurrenceResponse> upcoming(int days) {
        LocalDate limit = LocalDate.now().plusDays(Math.clamp(days, 1, MAX_UPCOMING_DAYS));
        List<UpcomingOccurrenceResponse> result = new ArrayList<>();

        for (RecurringTransaction recurring : recurringRepository.findAllByOwner(currentUser.id())) {
            if (!recurring.isActive() || recurring.getNextDate() == null) {
                continue;
            }
            int index = recurring.getOccurrenceCount();
            LocalDate date = recurring.getNextDate();
            while (!date.isAfter(limit) && (recurring.getEndDate() == null || !date.isAfter(recurring.getEndDate()))
                    && result.size() < 500) {
                result.add(new UpcomingOccurrenceResponse(
                        recurring.getId(),
                        date,
                        recurring.getLabel(),
                        recurring.isTransfer() ? TransactionDirection.DEBIT : recurring.getDirection(),
                        recurring.getAmount(),
                        recurring.getAccount().getId(),
                        recurring.getAccount().getName(),
                        recurring.getAccount().getCurrency().getCode(),
                        recurring.isTransfer(),
                        recurring.isTransfer() ? recurring.getTargetAccount().getName() : null));
                index++;
                date = recurring.getFrequency().occurrence(recurring.getStartDate(), recurring.getInterval(), index);
            }
        }
        result.sort(Comparator.comparing(UpcomingOccurrenceResponse::date).thenComparing(UpcomingOccurrenceResponse::label));
        return result;
    }

    // Génère les occurrences échues, chaque récurrence dans sa propre transaction
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "${application.recurring.cron}")
    public void generateAllDue() {
        List<Long> ids = recurringRepository.findDueIds(LocalDate.now());
        ids.forEach(this::generateSafely);
        if (!ids.isEmpty()) {
            log.info("{} recurring transactions processed", ids.size());
        }
    }

    public void generateDueForUser(UUID userId) {
        recurringRepository.findDueIdsByOwner(userId, LocalDate.now()).forEach(this::generateSafely);
    }

    private void generateSafely(Long recurringId) {
        try {
            transactionTemplate.executeWithoutResult(status -> generate(recurringId));
        } catch (RuntimeException exception) {
            log.error("Failed to generate recurring transaction {}", recurringId, exception);
        }
    }

    private void generate(Long recurringId) {
        RecurringTransaction recurring = recurringRepository.findById(recurringId).orElse(null);
        if (recurring == null || !recurring.isActive() || recurring.getNextDate() == null) {
            return;
        }

        LocalDate today = LocalDate.now();
        int generated = 0;
        while (recurring.isActive() && recurring.getNextDate() != null
                && !recurring.getNextDate().isAfter(today) && generated < MAX_OCCURRENCES_PER_RUN) {

            Account account = recurring.getAccount();
            Account target = recurring.getTargetAccount();
            if (account.isClosed() || (target != null && target.isClosed())) {
                log.warn("Recurring transaction {} disabled : account closed", recurringId);
                recurring.setActive(false);
                break;
            }

            LocalDate date = recurring.getNextDate();
            if (recurring.isTransfer()) {
                transferService.createPair(account, target, recurring.getAmount(),
                        transferService.resolveTargetAmount(account, target, recurring.getAmount(), recurring.getTargetAmount(), date),
                        recurring.getLabel(), null, date, recurring, TransactionStatus.PENDING);
            } else {
                transactionRepository.save(Transaction.builder()
                        .account(account)
                        .direction(recurring.getDirection())
                        .amount(recurring.getAmount())
                        .label(recurring.getLabel())
                        .payee(recurring.getPayee())
                        .category(recurring.getCategory())
                        .paymentMethod(recurring.getPaymentMethod())
                        .operationDate(date)
                        .status(TransactionStatus.PENDING)
                        .recurringTransaction(recurring)
                        .createdAt(LocalDateTime.now())
                        .build());
            }
            generated++;
            schedule(recurring, recurring.getOccurrenceCount() + 1, true);
        }
    }

    /**
     * Positionne la prochaine occurrence à l'index donné ; désactive la récurrence si elle dépasse la date de fin.
     */
    private static void schedule(RecurringTransaction recurring, int index, boolean active) {
        recurring.setOccurrenceCount(index);
        LocalDate next = recurring.getFrequency().occurrence(recurring.getStartDate(), recurring.getInterval(), index);
        if (recurring.getEndDate() != null && next.isAfter(recurring.getEndDate())) {
            recurring.setNextDate(null);
            recurring.setActive(false);
        } else {
            recurring.setNextDate(next);
            recurring.setActive(active);
        }
    }

    private void apply(RecurringTransaction recurring, RecurringTransactionRequest request) {
        Account account = accountService.find(request.accountId());
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new BadRequestException("La date de fin doit être postérieure à la date de début");
        }

        if (request.targetAccountId() != null) {
            Account target = accountService.find(request.targetAccountId());
            if (target.getId().equals(account.getId())) {
                throw new BadRequestException("Les comptes débité et crédité doivent être différents");
            }
            recurring.setTargetAccount(target);
            recurring.setDirection(TransactionDirection.DEBIT);
            recurring.setCategory(null);
            recurring.setTargetAmount(account.getCurrency().getId().equals(target.getCurrency().getId())
                    ? null : request.targetAmount());
        } else {
            if (request.direction() == null) {
                throw new BadRequestException("Le sens de l'opération est obligatoire");
            }
            recurring.setTargetAccount(null);
            recurring.setTargetAmount(null);
            recurring.setDirection(request.direction());
            recurring.setCategory(request.categoryId() == null ? null : categoryService.find(request.categoryId()));
        }

        recurring.setAccount(account);
        recurring.setAmount(request.amount());
        recurring.setLabel(request.label().trim());
        recurring.setPayee(AccountService.blankToNull(request.payee()));
        recurring.setPaymentMethod(request.paymentMethod());
        recurring.setFrequency(request.frequency());
        recurring.setInterval(request.interval());
        recurring.setStartDate(request.startDate());
        recurring.setEndDate(request.endDate());
    }

    private RecurringTransaction find(Long id) {
        return recurringRepository.findByIdAndOwner(id, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("Opération récurrente introuvable"));
    }
}
