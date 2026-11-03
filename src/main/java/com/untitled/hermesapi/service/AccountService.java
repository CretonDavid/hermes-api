package com.untitled.hermesapi.service;

import com.untitled.hermesapi.dto.account.AccountRequest;
import com.untitled.hermesapi.dto.account.AccountResponse;
import com.untitled.hermesapi.exception.ResourceNotFoundException;
import com.untitled.hermesapi.mapper.AccountMapper;
import com.untitled.hermesapi.model.Account;
import com.untitled.hermesapi.repository.AccountRepository;
import com.untitled.hermesapi.repository.ReconciliationRepository;
import com.untitled.hermesapi.repository.RecurringTransactionRepository;
import com.untitled.hermesapi.repository.TransactionRepository;
import com.untitled.hermesapi.repository.TransactionRepository.AccountBalanceView;
import com.untitled.hermesapi.utils.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final ReconciliationRepository reconciliationRepository;
    private final RecurringTransactionRepository recurringTransactionRepository;
    private final AccountTypeService accountTypeService;
    private final CurrencyService currencyService;
    private final AccountMapper accountMapper;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public List<AccountResponse> getAll() {
        UUID userId = currentUser.id();
        Map<Long, AccountBalanceView> balances = balances(userId);
        return accountRepository.findAllByOwner(userId).stream()
                .map(account -> accountMapper.toResponse(account, balances.get(account.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse getById(Long id) {
        Account account = find(id);
        return accountMapper.toResponse(account, balances(currentUser.id()).get(id));
    }

    @Transactional
    public AccountResponse create(AccountRequest request) {
        Account account = Account.builder()
                .owner(currentUser.reference())
                .createdAt(LocalDateTime.now())
                .build();
        apply(account, request);

        accountRepository.save(account);
        log.info("Account {} created", account.getId());

        return accountMapper.toResponse(account, null);
    }

    @Transactional
    public AccountResponse update(Long id, AccountRequest request) {
        Account account = find(id);
        apply(account, request);
        log.info("Account {} updated", id);

        return accountMapper.toResponse(account, balances(currentUser.id()).get(id));
    }

    /**
     * Supprime le compte, ses opérations, ses rapprochements et ses récurrences. Les virements
     * vers d'autres comptes sont conservés côté autre compte, comme opérations simples.
     */
    @Transactional
    public void delete(Long id) {
        Account account = find(id);

        transactionRepository.unlinkTransfersOfAccount(id);
        transactionRepository.detachRecurringOfAccount(id);
        transactionRepository.deleteByAccountId(id);
        reconciliationRepository.deleteByAccountId(id);
        recurringTransactionRepository.deleteByAccountId(id);
        accountRepository.delete(account);
        log.info("Account {} deleted", id);
    }

    public Account find(Long id) {
        return accountRepository.findByIdAndOwner(id, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("Compte introuvable"));
    }

    private void apply(Account account, AccountRequest request) {
        account.setName(request.name().trim());
        account.setDescription(blankToNull(request.description()));
        account.setBankName(blankToNull(request.bankName()));
        account.setAccountType(accountTypeService.find(request.accountTypeId()));
        account.setCurrency(currencyService.findAccessible(request.currencyId()));
        account.setInitialBalance(request.initialBalance() == null ? BigDecimal.ZERO : request.initialBalance());
        account.setOpeningDate(request.openingDate() == null ? LocalDate.now() : request.openingDate());
        account.setClosed(request.closed());
    }

    private Map<Long, AccountBalanceView> balances(UUID userId) {
        return transactionRepository.balancesByOwner(userId, LocalDate.now()).stream()
                .collect(Collectors.toMap(AccountBalanceView::getAccountId, Function.identity()));
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
