package com.untitled.hermesapi.mapper;

import com.untitled.hermesapi.dto.recurring.RecurringTransactionResponse;
import com.untitled.hermesapi.dto.transaction.TransactionResponse;
import com.untitled.hermesapi.dto.transaction.TransactionResponse.TransferSummary;
import com.untitled.hermesapi.model.Account;
import com.untitled.hermesapi.model.RecurringTransaction;
import com.untitled.hermesapi.model.Transaction;
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
                entity.isTransfer()
        );
    }
}
