package com.hermes.api.mapper;

import com.hermes.api.dto.recurring.RecurringTransactionResponse;
import com.hermes.api.dto.recurring.ReimbursementPayerResponse;
import com.hermes.api.dto.transaction.TransactionResponse;
import com.hermes.api.dto.transaction.TransactionResponse.TransferSummary;
import com.hermes.api.model.Account;
import com.hermes.api.model.RecurringTransaction;
import com.hermes.api.model.ReimbursementPayer;
import com.hermes.api.model.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransactionMapper {

    private final CategoryMapper categoryMapper;

    public TransactionResponse toResponse(Transaction entity) {
        Account account = entity.getAccount();
        Transaction peer = entity.getTransferPeer();
        TransferSummary transfer = peer == null ? null
                : new TransferSummary(peer.getId(), peer.getAccount().getId(), peer.getAccount().getName());

        return new TransactionResponse(
                entity.getId(),
                account.getId(),
                account.getName(),
                account.getCurrency().getCode(),
                entity.getDirection(),
                entity.getAmount(),
                entity.getLabel(),
                entity.getPayee(),
                entity.getNotes(),
                categoryMapper.toSummary(entity.getCategory()),
                entity.getPaymentMethod(),
                entity.getOperationDate(),
                entity.getValueDate(),
                entity.getStatus(),
                transfer,
                entity.getRecurringTransaction() == null ? null : entity.getRecurringTransaction().getId(),
                reimbursement(entity),
                entity.getCreatedAt()
        );
    }

    public RecurringTransactionResponse toResponse(RecurringTransaction entity) {
        Account account = entity.getAccount();
        Account target = entity.getTargetAccount();

        return new RecurringTransactionResponse(
                entity.getId(),
                account.getId(),
                account.getName(),
                account.getCurrency().getCode(),
                target == null ? null : target.getId(),
                target == null ? null : target.getName(),
                target == null ? null : target.getCurrency().getCode(),
                entity.getDirection(),
                entity.getAmount(),
                entity.getTargetAmount(),
                entity.getLabel(),
                entity.getPayee(),
                categoryMapper.toSummary(entity.getCategory()),
                entity.getPaymentMethod(),
                entity.getFrequency(),
                entity.getInterval(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getNextDate(),
                entity.getOccurrenceCount(),
                entity.isActive(),
                entity.isTransfer(),
                entity.getReimbursementPayers().stream()
                        .map(p -> new ReimbursementPayerResponse(p.getId(), p.getName(), p.getShare(), p.getPeriod()))
                        .toList()
        );
    }

    private static TransactionResponse.ReimbursementSummary reimbursement(Transaction entity) {
        ReimbursementPayer payer = entity.getReimbursementPayer();
        if (payer == null) {
            return null;
        }
        RecurringTransaction recurring = payer.getRecurringTransaction();
        return new TransactionResponse.ReimbursementSummary(recurring.getId(), recurring.getLabel(),
                payer.getId(), payer.getName(), payer.getPeriod(), entity.getReimbursementPeriodStart());
    }
}
