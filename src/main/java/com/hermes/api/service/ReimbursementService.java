package com.hermes.api.service;

import com.hermes.api.dto.recurring.ReimbursementPayerRequest;
import com.hermes.api.dto.reimbursement.ReimbursementLinkRequest;
import com.hermes.api.dto.reimbursement.ReimbursementTrackingResponse;
import com.hermes.api.dto.reimbursement.ReimbursementTrackingResponse.Payment;
import com.hermes.api.dto.reimbursement.ReimbursementTrackingResponse.PayerTracking;
import com.hermes.api.dto.reimbursement.ReimbursementTrackingResponse.PeriodStatus;
import com.hermes.api.dto.reimbursement.ReimbursementTrackingResponse.Status;
import com.hermes.api.exception.BadRequestException;
import com.hermes.api.exception.ResourceNotFoundException;
import com.hermes.api.model.RecurringTransaction;
import com.hermes.api.model.ReimbursementPayer;
import com.hermes.api.model.Transaction;
import com.hermes.api.model.enums.ReimbursementPeriod;
import com.hermes.api.model.enums.TransactionDirection;
import com.hermes.api.repository.RecurringTransactionRepository;
import com.hermes.api.repository.TransactionRepository;
import com.hermes.api.utils.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dépenses récurrentes remboursées en partie par des tiers : pour chaque tiers et chaque période
 * (mois, trimestre ou année civile), ce qui est dû d'après les opérations réellement générées,
 * et ce qui a été reçu.
 */
@Service
@RequiredArgsConstructor
public class ReimbursementService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final RecurringTransactionRepository recurringRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final CurrentUser currentUser;

    /**
     * Met à jour les tiers d'une récurrence. Un tiers retiré perd ses remboursements rattachés,
     * qui redeviennent de simples revenus.
     */
    void apply(RecurringTransaction recurring, List<ReimbursementPayerRequest> requests) {
        List<ReimbursementPayerRequest> wanted = requests == null ? List.of() : requests;
        if (!wanted.isEmpty()) {
            if (recurring.isTransfer() || recurring.getDirection() != TransactionDirection.DEBIT) {
                throw new BadRequestException("Seule une dépense récurrente peut être remboursée par des tiers");
            }
            BigDecimal total = wanted.stream().map(ReimbursementPayerRequest::share).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.compareTo(HUNDRED) > 0) {
                throw new BadRequestException("Les parts remboursées par les tiers dépassent 100 %");
            }
        }

        Map<Long, ReimbursementPayer> existing = recurring.getReimbursementPayers().stream()
                .collect(Collectors.toMap(ReimbursementPayer::getId, Function.identity()));
        Set<Long> kept = new HashSet<>();
        List<ReimbursementPayer> added = new ArrayList<>();
        for (ReimbursementPayerRequest request : wanted) {
            ReimbursementPayer payer = request.id() == null ? null : existing.get(request.id());
            if (payer == null) {
                payer = ReimbursementPayer.builder().recurringTransaction(recurring).build();
                added.add(payer);
            } else {
                kept.add(payer.getId());
            }
            payer.setName(request.name().trim());
            payer.setShare(request.share());
            ReimbursementPeriod period = request.period() == null ? ReimbursementPeriod.YEARLY : request.period();
            payer.setPeriod(period);
            // Un nouveau tiers doit par défaut à partir de la période en cours
            LocalDate start = request.startDate() != null ? request.startDate()
                    : payer.getStartDate() != null ? payer.getStartDate()
                    : payer.getId() == null ? LocalDate.now() : null;
            payer.setStartDate(start == null ? null : period.start(start));
        }

        List<Long> removed = existing.keySet().stream().filter(id -> !kept.contains(id)).toList();
        if (!removed.isEmpty()) {
            transactionRepository.detachReimbursementPayers(removed);
            recurring.getReimbursementPayers().removeIf(p -> removed.contains(p.getId()));
        }
        recurring.getReimbursementPayers().addAll(added);
    }

    @Transactional(readOnly = true)
    public List<ReimbursementTrackingResponse> tracking() {
        List<RecurringTransaction> reimbursed = recurringRepository.findAllByOwner(currentUser.id()).stream()
                .filter(RecurringTransaction::isReimbursed)
                .toList();
        if (reimbursed.isEmpty()) {
            return List.of();
        }

        Map<Long, List<Transaction>> expenses = new HashMap<>();
        Map<Long, List<Transaction>> payments = new HashMap<>();
        for (Transaction t : transactionRepository.findForReimbursements(reimbursed.stream().map(RecurringTransaction::getId).toList())) {
            if (t.getRecurringTransaction() != null && t.getDirection() == TransactionDirection.DEBIT) {
                expenses.computeIfAbsent(t.getRecurringTransaction().getId(), id -> new ArrayList<>()).add(t);
            }
            if (t.getReimbursementPayer() != null) {
                payments.computeIfAbsent(t.getReimbursementPayer().getId(), id -> new ArrayList<>()).add(t);
            }
        }

        LocalDate today = LocalDate.now();
        return reimbursed.stream()
                .map(r -> new ReimbursementTrackingResponse(
                        r.getId(),
                        r.getLabel(),
                        r.getAccount().getCurrency().getCode(),
                        r.getReimbursementPayers().stream()
                                .map(p -> track(r, p, expenses.getOrDefault(r.getId(), List.of()),
                                        payments.getOrDefault(p.getId(), List.of()), today))
                                .toList()))
                .toList();
    }

    @Transactional
    public void link(Long recurringId, ReimbursementLinkRequest request) {
        RecurringTransaction recurring = recurringRepository.findByIdAndOwner(recurringId, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("Opération récurrente introuvable"));
        ReimbursementPayer payer = recurring.getReimbursementPayers().stream()
                .filter(p -> p.getId().equals(request.payerId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Tiers introuvable pour cette opération récurrente"));

        Transaction transaction = transactionService.find(request.transactionId());
        if (transaction.isTransfer() || transaction.getDirection() != TransactionDirection.CREDIT) {
            throw new BadRequestException("Seule une opération reçue peut être rattachée à un remboursement");
        }
        String expected = recurring.getAccount().getCurrency().getCode();
        if (!transaction.getAccount().getCurrency().getCode().equals(expected)) {
            throw new BadRequestException("Le remboursement doit être dans la même devise que la dépense (" + expected + ")");
        }
        transaction.setReimbursementPayer(payer);
        transaction.setReimbursementPeriodStart(payer.getPeriod().start(request.period()));
        // Dans la catégorie de la dépense, le remboursement vient en diminution de celle-ci dans les statistiques
        if (recurring.getCategory() != null) {
            transaction.setCategory(recurring.getCategory());
        }
    }

    @Transactional
    public void unlink(Long recurringId, Long transactionId) {
        Transaction transaction = transactionService.find(transactionId);
        ReimbursementPayer payer = transaction.getReimbursementPayer();
        if (payer == null || !payer.getRecurringTransaction().getId().equals(recurringId)) {
            throw new BadRequestException("Cette opération n'est pas rattachée à ce remboursement");
        }
        transaction.setReimbursementPayer(null);
        transaction.setReimbursementPeriodStart(null);
    }

    private PayerTracking track(RecurringTransaction recurring, ReimbursementPayer payer, List<Transaction> expenses,
                                List<Transaction> payments, LocalDate today) {
        ReimbursementPeriod period = payer.getPeriod();

        Map<LocalDate, BigDecimal> spentByPeriod = new HashMap<>();
        for (Transaction t : expenses) {
            spentByPeriod.merge(period.start(t.getOperationDate()), t.getAmount(), BigDecimal::add);
        }
        // La période d'un remboursement est recalculée si le rythme du tiers a changé depuis
        Map<LocalDate, List<Transaction>> paidByPeriod = new HashMap<>();
        for (Transaction t : payments) {
            paidByPeriod.computeIfAbsent(period.start(t.getReimbursementPeriodStart()), d -> new ArrayList<>()).add(t);
        }

        // De la première période due jusqu'à la période en cours (ou une période future déjà payée d'avance)
        LocalDate current = period.start(today);
        LocalDate first = payer.firstPeriod();
        LocalDate last = current;
        if (recurring.getEndDate() != null && recurring.getEndDate().isBefore(current)) {
            last = period.start(recurring.getEndDate());
        }
        // Un versement rattaché à une période antérieure au début (date avancée depuis) compte comme une avance
        BigDecimal advance = BigDecimal.ZERO;
        for (Map.Entry<LocalDate, List<Transaction>> e : paidByPeriod.entrySet()) {
            if (e.getKey().isBefore(first)) {
                advance = advance.add(sum(e.getValue()));
            } else if (e.getKey().isAfter(last)) {
                last = e.getKey();
            }
        }

        // Dans l'ordre chronologique, ce qui est versé en trop est reporté sur les périodes suivantes
        List<PeriodStatus> periods = new ArrayList<>();
        for (LocalDate start = first; !start.isAfter(last); start = period.next(start)) {
            List<Transaction> received = paidByPeriod.getOrDefault(start, List.of());
            if (start.isAfter(current) && received.isEmpty() && advance.signum() == 0) {
                continue;
            }
            PeriodStatus status = periodStatus(start, period.end(start), today,
                    spentByPeriod.getOrDefault(start, BigDecimal.ZERO), payer.getShare(), received, advance);
            advance = status.surplus();
            periods.add(status);
        }
        Collections.reverse(periods);

        return new PayerTracking(payer.getId(), payer.getName(), payer.getShare(), period, periods);
    }

    private static PeriodStatus periodStatus(LocalDate start, LocalDate end, LocalDate today, BigDecimal spent,
                                             BigDecimal share, List<Transaction> received, BigDecimal advance) {
        BigDecimal due = spent.multiply(share).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal paid = sum(received);
        BigDecimal advanceUsed = advance.min(due);
        BigDecimal remaining = due.subtract(advanceUsed).subtract(paid).max(BigDecimal.ZERO);
        BigDecimal surplus = advance.add(paid).subtract(due).max(BigDecimal.ZERO);
        boolean ended = end.isBefore(today);

        Status status;
        if (remaining.signum() == 0) {
            status = Status.PAID;
        } else if (!ended) {
            status = Status.IN_PROGRESS;
        } else {
            status = paid.signum() > 0 ? Status.PARTIAL : Status.UNPAID;
        }

        List<Payment> list = received.stream()
                .sorted(Comparator.comparing(Transaction::getOperationDate))
                .map(t -> new Payment(t.getId(), t.getOperationDate(), t.getAmount(), t.getLabel(), t.getAccount().getName()))
                .toList();
        return new PeriodStatus(start, end, ended, spent, due, paid, advanceUsed, remaining, surplus, status, list);
    }

    private static BigDecimal sum(List<Transaction> transactions) {
        return transactions.stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
