package com.untitled.hermesapi.service;

import com.untitled.hermesapi.dto.account.AccountTypeRequest;
import com.untitled.hermesapi.dto.account.AccountTypeResponse;
import com.untitled.hermesapi.exception.BadRequestException;
import com.untitled.hermesapi.exception.ResourceAlreadyExistsException;
import com.untitled.hermesapi.exception.ResourceNotFoundException;
import com.untitled.hermesapi.mapper.AccountMapper;
import com.untitled.hermesapi.model.AccountType;
import com.untitled.hermesapi.repository.AccountRepository;
import com.untitled.hermesapi.repository.AccountTypeRepository;
import com.untitled.hermesapi.utils.CurrentUser;
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
