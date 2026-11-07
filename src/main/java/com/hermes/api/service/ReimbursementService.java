package com.hermes.api.service;

import com.hermes.api.dto.reimbursement.ReimbursementLinkRequest;
import com.hermes.api.dto.reimbursement.ReimbursementTrackingResponse;
import com.hermes.api.dto.reimbursement.ReimbursementTrackingResponse.Payment;
import com.hermes.api.dto.reimbursement.ReimbursementTrackingResponse.PeriodStatus;
import com.hermes.api.dto.reimbursement.ReimbursementTrackingResponse.Status;
import com.hermes.api.exception.BadRequestException;
import com.hermes.api.exception.ResourceNotFoundException;
import com.hermes.api.model.RecurringTransaction;
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

/**
 * Suivi des dépenses récurrentes remboursées par un tiers : pour chaque période (mois, trimestre ou année civile),
 * ce qui est dû d'après les opérations réellement générées, et ce qui a été reçu.
 */
@Service
@RequiredArgsConstructor
public class ReimbursementService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final RecurringTransactionRepository recurringRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final CurrentUser currentUser;

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
            if (t.getReimbursedRecurring() != null) {
                payments.computeIfAbsent(t.getReimbursedRecurring().getId(), id -> new ArrayList<>()).add(t);
            }
        }

        LocalDate today = LocalDate.now();
        return reimbursed.stream()
                .map(r -> track(r, expenses.getOrDefault(r.getId(), List.of()), payments.getOrDefault(r.getId(), List.of()), today))
                .toList();
    }

    @Transactional
    public void link(Long recurringId, ReimbursementLinkRequest request) {
        RecurringTransaction recurring = findReimbursed(recurringId);
        Transaction transaction = transactionService.find(request.transactionId());
        if (transaction.isTransfer() || transaction.getDirection() != TransactionDirection.CREDIT) {
            throw new BadRequestException("Seule une opération reçue peut être rattachée à un remboursement");
        }
        String expected = recurring.getAccount().getCurrency().getCode();
        if (!transaction.getAccount().getCurrency().getCode().equals(expected)) {
            throw new BadRequestException("Le remboursement doit être dans la même devise que la dépense (" + expected + ")");
        }
        transaction.setReimbursedRecurring(recurring);
        transaction.setReimbursementPeriodStart(recurring.getReimbursementPeriod().start(request.period()));
        // Dans la catégorie de la dépense, le remboursement vient en diminution de celle-ci dans les statistiques
        if (recurring.getCategory() != null) {
            transaction.setCategory(recurring.getCategory());
        }
    }

    @Transactional
    public void unlink(Long recurringId, Long transactionId) {
        Transaction transaction = transactionService.find(transactionId);
        if (transaction.getReimbursedRecurring() == null || !transaction.getReimbursedRecurring().getId().equals(recurringId)) {
            throw new BadRequestException("Cette opération n'est pas rattachée à ce remboursement");
        }
        transaction.setReimbursedRecurring(null);
        transaction.setReimbursementPeriodStart(null);
    }

    private ReimbursementTrackingResponse track(RecurringTransaction recurring, List<Transaction> expenses,
                                                List<Transaction> payments, LocalDate today) {
        ReimbursementPeriod period = recurring.getReimbursementPeriod();
        int share = recurring.getReimbursementShare();

        Map<LocalDate, BigDecimal> spentByPeriod = new HashMap<>();
        for (Transaction t : expenses) {
            spentByPeriod.merge(period.start(t.getOperationDate()), t.getAmount(), BigDecimal::add);
        }
        Map<LocalDate, List<Transaction>> paidByPeriod = new HashMap<>();
        for (Transaction t : payments) {
            paidByPeriod.computeIfAbsent(period.start(t.getReimbursementPeriodStart()), d -> new ArrayList<>()).add(t);
        }

        // De la première période concernée jusqu'à la période en cours (ou une période future déjà payée d'avance)
        LocalDate current = period.start(today);
        LocalDate first = period.start(recurring.getStartDate());
        LocalDate last = current;
        if (recurring.getEndDate() != null && recurring.getEndDate().isBefore(current)) {
            last = period.start(recurring.getEndDate());
        }
        for (LocalDate d : spentByPeriod.keySet()) {
            first = d.isBefore(first) ? d : first;
        }
        for (LocalDate d : paidByPeriod.keySet()) {
            first = d.isBefore(first) ? d : first;
            last = d.isAfter(last) ? d : last;
        }

        List<PeriodStatus> periods = new ArrayList<>();
        for (LocalDate start = first; !start.isAfter(last); start = period.next(start)) {
            BigDecimal spent = spentByPeriod.getOrDefault(start, BigDecimal.ZERO);
            List<Transaction> received = paidByPeriod.getOrDefault(start, List.of());
            if (start.isAfter(current) && received.isEmpty()) {
                continue;
            }
            periods.add(periodStatus(start, period.end(start), today, spent, share, received));
        }
        Collections.reverse(periods);

        return new ReimbursementTrackingResponse(
                recurring.getId(),
                recurring.getLabel(),
                recurring.getReimbursementPayer(),
                share,
                period,
                recurring.getAccount().getCurrency().getCode(),
                periods);
    }

    private static PeriodStatus periodStatus(LocalDate start, LocalDate end, LocalDate today, BigDecimal spent,
                                             int share, List<Transaction> received) {
        BigDecimal due = spent.multiply(BigDecimal.valueOf(share)).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal paid = received.stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = due.subtract(paid).max(BigDecimal.ZERO);
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
        return new PeriodStatus(start, end, ended, spent, due, paid, remaining, status, list);
    }

    private RecurringTransaction findReimbursed(Long id) {
        RecurringTransaction recurring = recurringRepository.findByIdAndOwner(id, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("Opération récurrente introuvable"));
        if (!recurring.isReimbursed()) {
            throw new BadRequestException("Cette opération récurrente n'est remboursée par personne");
        }
        return recurring;
    }
}
