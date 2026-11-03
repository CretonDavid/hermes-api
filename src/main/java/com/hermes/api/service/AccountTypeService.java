package com.hermes.api.service;

import com.hermes.api.dto.account.AccountTypeRequest;
import com.hermes.api.dto.account.AccountTypeResponse;
import com.hermes.api.exception.BadRequestException;
import com.hermes.api.exception.ResourceAlreadyExistsException;
import com.hermes.api.exception.ResourceNotFoundException;
import com.hermes.api.mapper.AccountMapper;
import com.hermes.api.model.AccountType;
import com.hermes.api.repository.AccountRepository;
import com.hermes.api.repository.AccountTypeRepository;
import com.hermes.api.utils.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountTypeService {

    private final AccountTypeRepository accountTypeRepository;
    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public List<AccountTypeResponse> findAll() {
        return accountTypeRepository.findByOwner_IdOrderByNameAsc(currentUser.id()).stream()
                .map(accountMapper::toResponse)
                .toList();
    }

    @Transactional
    public AccountTypeResponse create(AccountTypeRequest request) {
        String name = request.name().trim();
        if (accountTypeRepository.existsByOwner_IdAndNameIgnoreCase(currentUser.id(), name)) {
            throw new ResourceAlreadyExistsException("Le type de compte " + name + " existe déjà");
        }
        AccountType type = AccountType.builder()
                .name(name)
                .savings(request.savings())
                .owner(currentUser.reference())
                .build();
        return accountMapper.toResponse(accountTypeRepository.save(type));
    }

    @Transactional
    public AccountTypeResponse update(Long id, AccountTypeRequest request) {
        UUID userId = currentUser.id();
        AccountType type = find(id);
        String name = request.name().trim();
        if (!type.getName().equalsIgnoreCase(name) && accountTypeRepository.existsByOwner_IdAndNameIgnoreCase(userId, name)) {
            throw new ResourceAlreadyExistsException("Le type de compte " + name + " existe déjà");
        }
        type.setName(name);
        type.setSavings(request.savings());
        return accountMapper.toResponse(type);
    }

    @Transactional
    public void delete(Long id) {
        AccountType type = find(id);
        if (accountRepository.existsByAccountType_Id(id)) {
            throw new BadRequestException("Ce type est utilisé par au moins un compte : changez d'abord le type de ces comptes");
        }
        accountTypeRepository.delete(type);
    }

    public AccountType find(Long id) {
        return accountTypeRepository.findByIdAndOwner_Id(id, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("Type de compte introuvable"));
    }
}
