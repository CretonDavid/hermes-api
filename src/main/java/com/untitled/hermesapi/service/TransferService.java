package com.untitled.hermesapi.service;

import com.untitled.hermesapi.dto.transaction.TransferRequest;
import com.untitled.hermesapi.dto.transaction.TransferResponse;
import com.untitled.hermesapi.exception.BadRequestException;
import com.untitled.hermesapi.mapper.TransactionMapper;
import com.untitled.hermesapi.model.Account;
import com.untitled.hermesapi.model.RecurringTransaction;
import com.untitled.hermesapi.model.Transaction;
import com.untitled.hermesapi.model.enums.PaymentMethod;
import com.untitled.hermesapi.model.enums.TransactionDirection;
import com.untitled.hermesapi.model.enums.TransactionStatus;
import com.untitled.hermesapi.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Un virement entre deux comptes de l'utilisateur est représenté par deux opérations liées :
 * un débit sur le compte source et un crédit sur le compte cible.
 */
@Service
@RequiredArgsConstructor
public class TransferService {

    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final AccountService accountService;
    private final ExchangeRateService exchangeRateService;
    private final TransactionMapper transactionMapper;

    @Transactional(readOnly = true)
    public TransferResponse getById(Long transactionId) {
        Transaction transaction = transactionService.find(transactionId);
        if (!transaction.isTransfer()) {
            throw new BadRequestException("Cette opération n'est pas un virement");
        }
        return toResponse(transaction);
    }

    @Transactional
    public TransferResponse create(TransferRequest request) {
        Account from = accountService.find(request.fromAccountId());
        Account to = accountService.find(request.toAccountId());
        validate(from, to);

        BigDecimal targetAmount = resolveTargetAmount(from, to, request.amount(), request.targetAmount(), request.operationDate());
        String label = request.label() == null || request.label().isBlank() ? null : request.label().trim();
        return toResponse(createPair(from, to, request.amount(), targetAmount, label,
                AccountService.blankToNull(request.notes()), request.operationDate(), null, TransactionStatus.PENDING));
    }

    @Transactional
    public TransferResponse update(Long transactionId, TransferRequest request) {
        Transaction transaction = transactionService.find(transactionId);
        if (!transaction.isTransfer()) {
            throw new BadRequestException("Cette opération n'est pas un virement");
        }
        Transaction debit = transaction.getDirection() == TransactionDirection.DEBIT ? transaction : transaction.getTransferPeer();
        Transaction credit = debit.getTransferPeer();
        if (debit.getStatus() == TransactionStatus.RECONCILED || credit.getStatus() == TransactionStatus.RECONCILED) {
            throw new BadRequestException("Un virement rapproché ne peut pas être modifié");
        }

        Account from = accountService.find(request.fromAccountId());
        Account to = accountService.find(request.toAccountId());
        validate(from, to);
        BigDecimal targetAmount = resolveTargetAmount(from, to, request.amount(), request.targetAmount(), request.operationDate());
        String label = request.label() == null || request.label().isBlank() ? null : request.label().trim();
        String notes = AccountService.blankToNull(request.notes());

        if (!debit.getAccount().getId().equals(from.getId())) {
            debit.setStatus(TransactionStatus.PENDING);
        }
        if (!credit.getAccount().getId().equals(to.getId())) {
            credit.setStatus(TransactionStatus.PENDING);
        }
        fill(debit, from, to, request.amount(), label, notes, request.operationDate());
        fill(credit, to, from, targetAmount, label, notes, request.operationDate());
        return new TransferResponse(transactionMapper.toResponse(debit), transactionMapper.toResponse(credit));
    }

    /**
     * Crée les deux opérations d'un virement. Utilisé aussi par les virements récurrents.
     */
    Transaction createPair(Account from, Account to, BigDecimal amount, BigDecimal targetAmount, String label,
                           String notes, LocalDate date, RecurringTransaction recurring, TransactionStatus status) {
        LocalDateTime now = LocalDateTime.now();
        Transaction debit = Transaction.builder()
                .direction(TransactionDirection.DEBIT)
                .status(status)
                .recurringTransaction(recurring)
                .createdAt(now)
                .build();
        fill(debit, from, to, amount, label, notes, date);

        Transaction credit = Transaction.builder()
                .direction(TransactionDirection.CREDIT)
                .status(status)
                .recurringTransaction(recurring)
                .createdAt(now)
                .build();
        fill(credit, to, from, targetAmount, label, notes, date);

        transactionRepository.save(debit);
        credit.setTransferPeer(debit);
        transactionRepository.save(credit);
        debit.setTransferPeer(credit);
        return debit;
    }

    BigDecimal resolveTargetAmount(Account from, Account to, BigDecimal amount, BigDecimal targetAmount, LocalDate date) {
        if (from.getCurrency().getId().equals(to.getCurrency().getId())) {
            return amount;
        }
        if (targetAmount != null) {
            return targetAmount;
        }
        UUID userId = from.getOwner().getId();
        return exchangeRateService.loadTable(userId)
                .convert(amount, from.getCurrency().getCode(), to.getCurrency().getCode(), date)
                .map(converted -> converted.setScale(2, RoundingMode.HALF_EVEN))
                .orElseThrow(() -> new BadRequestException(
                        "Aucun taux " + from.getCurrency().getCode() + "/" + to.getCurrency().getCode()
                                + " connu : indiquez le montant crédité"));
    }

    private static void validate(Account from, Account to) {
        if (from.getId().equals(to.getId())) {
            throw new BadRequestException("Les comptes débité et crédité doivent être différents");
        }
        TransactionService.ensureOpen(from);
        TransactionService.ensureOpen(to);
    }

    private static void fill(Transaction transaction, Account account, Account other, BigDecimal amount,
                             String label, String notes, LocalDate date) {
        transaction.setAccount(account);
        transaction.setAmount(amount);
        transaction.setLabel(label != null ? label
                : transaction.getDirection() == TransactionDirection.DEBIT
                ? "Virement vers " + other.getName()
                : "Virement depuis " + other.getName());
        transaction.setPayee(other.getName());
        transaction.setNotes(notes);
        transaction.setCategory(null);
        transaction.setPaymentMethod(PaymentMethod.TRANSFER);
        transaction.setOperationDate(date);
    }

    private TransferResponse toResponse(Transaction transaction) {
        Transaction debit = transaction.getDirection() == TransactionDirection.DEBIT ? transaction : transaction.getTransferPeer();
        return new TransferResponse(transactionMapper.toResponse(debit), transactionMapper.toResponse(debit.getTransferPeer()));
    }
}
