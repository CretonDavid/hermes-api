package com.hermes.api.mapper;

import com.hermes.api.dto.account.AccountResponse;
import com.hermes.api.dto.account.AccountTypeResponse;
import com.hermes.api.dto.account.ReconciliationResponse;
import com.hermes.api.model.Account;
import com.hermes.api.model.AccountType;
import com.hermes.api.model.Reconciliation;
import com.hermes.api.repository.TransactionRepository.AccountBalanceView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class AccountMapper {

    private final CurrencyMapper currencyMapper;

    public AccountTypeResponse toResponse(AccountType entity) {
        return new AccountTypeResponse(entity.getId(), entity.getName(), entity.isSavings());
    }

    public AccountResponse toResponse(Account entity, AccountBalanceView balances) {
        BigDecimal initial = entity.getInitialBalance();
        BigDecimal total = balances == null ? BigDecimal.ZERO : orZero(balances.getTotal());
        BigDecimal current = balances == null ? BigDecimal.ZERO : orZero(balances.getCurrent());
        BigDecimal cleared = balances == null ? BigDecimal.ZERO : orZero(balances.getCleared());

        return new AccountResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getBankName(),
                toResponse(entity.getAccountType()),
                currencyMapper.toResponse(entity.getCurrency()),
                initial,
                initial.add(current),
                initial.add(total),
                initial.add(cleared),
                entity.getOpeningDate(),
                entity.isClosed()
        );
    }

    public ReconciliationResponse toResponse(Reconciliation entity) {
        if (entity == null) {
            return null;
        }
        return new ReconciliationResponse(
                entity.getId(),
                entity.getStatementDate(),
                entity.getStatementBalance(),
                entity.getTransactionCount(),
                entity.getCreatedAt()
        );
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
