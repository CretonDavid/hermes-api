package com.untitled.hermesapi.service;

import com.untitled.hermesapi.dto.account.ReconciliationRequest;
import com.untitled.hermesapi.dto.account.ReconciliationResponse;
import com.untitled.hermesapi.dto.account.ReconciliationStatusResponse;
import com.untitled.hermesapi.exception.BadRequestException;
import com.untitled.hermesapi.exception.ResourceNotFoundException;
import com.untitled.hermesapi.mapper.AccountMapper;
import com.untitled.hermesapi.model.Account;
import com.untitled.hermesapi.model.Reconciliation;
import com.untitled.hermesapi.repository.ReconciliationRepository;
import com.untitled.hermesapi.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Rapprochement bancaire : l'utilisateur pointe les opérations présentes sur son relevé, puis
 * valide le rapprochement lorsque le solde pointé correspond au solde du relevé. Les opérations
 * pointées deviennent alors rapprochées et ne peuvent plus être modifiées.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private final ReconciliationRepository reconciliationRepository;
    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final AccountMapper accountMapper;

    @Transactional(readOnly = true)
    public List<ReconciliationResponse> findAll(Long accountId) {
        accountService.find(accountId);
        return reconciliationRepository.findByAccount_IdOrderByStatementDateDescIdDesc(accountId).stream()
                .map(accountMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReconciliationStatusResponse status(Long accountId) {
        Account account = accountService.find(accountId);
        return new ReconciliationStatusResponse(
                clearedBalance(account),
                transactionRepository.countCleared(accountId),
                accountMapper.toResponse(reconciliationRepository
                        .findFirstByAccount_IdOrderByStatementDateDescIdDesc(accountId).orElse(null)));
    }

    @Transactional
    public ReconciliationResponse reconcile(Long accountId, ReconciliationRequest request) {
        Account account = accountService.find(accountId);

        BigDecimal cleared = clearedBalance(account);
        BigDecimal expected = request.statementBalance().setScale(2, RoundingMode.HALF_EVEN);
        if (cleared.compareTo(expected) != 0) {
            throw new BadRequestException("Le solde pointé (" + cleared.toPlainString()
                    + ") ne correspond pas au solde du relevé (" + expected.toPlainString()
                    + "), écart : " + expected.subtract(cleared).toPlainString());
        }

        reconciliationRepository.findFirstByAccount_IdOrderByStatementDateDescIdDesc(accountId)
                .filter(last -> last.getStatementDate().isAfter(request.statementDate()))
                .ifPresent(last -> {
                    throw new BadRequestException("La date du relevé est antérieure au dernier rapprochement");
                });

        Reconciliation reconciliation = Reconciliation.builder()
                .account(account)
                .statementDate(request.statementDate())
                .statementBalance(expected)
                .createdAt(LocalDateTime.now())
                .build();
        reconciliationRepository.save(reconciliation);

        int count = transactionRepository.markReconciled(accountId, reconciliation);
        reconciliation.setTransactionCount(count);
        log.info("Account {} reconciled : {} transactions", accountId, count);

        return accountMapper.toResponse(reconciliation);
    }

    /**
     * Annule le dernier rapprochement : ses opérations redeviennent simplement pointées.
     */
    @Transactional
    public void undoLast(Long accountId, Long reconciliationId) {
        accountService.find(accountId);
        Reconciliation last = reconciliationRepository.findFirstByAccount_IdOrderByStatementDateDescIdDesc(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Aucun rapprochement pour ce compte"));
        if (!last.getId().equals(reconciliationId)) {
            throw new BadRequestException("Seul le dernier rapprochement peut être annulé");
        }
        transactionRepository.undoReconciliation(reconciliationId);
        reconciliationRepository.delete(last);
    }

    private BigDecimal clearedBalance(Account account) {
        return account.getInitialBalance()
                .add(transactionRepository.clearedSum(account.getId()))
                .setScale(2, RoundingMode.HALF_EVEN);
    }
}
