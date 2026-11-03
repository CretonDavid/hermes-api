package com.hermes.api.service;

import com.hermes.api.dto.currency.CurrencyRequest;
import com.hermes.api.dto.currency.CurrencyResponse;
import com.hermes.api.exception.BadRequestException;
import com.hermes.api.exception.ResourceAlreadyExistsException;
import com.hermes.api.exception.ResourceNotFoundException;
import com.hermes.api.mapper.CurrencyMapper;
import com.hermes.api.model.Currency;
import com.hermes.api.model.User;
import com.hermes.api.repository.AccountRepository;
import com.hermes.api.repository.CurrencyRepository;
import com.hermes.api.repository.ExchangeRateRepository;
import com.hermes.api.repository.UserRepository;
import com.hermes.api.utils.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CurrencyService {

    private final CurrencyRepository currencyRepository;
    private final AccountRepository accountRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final UserRepository userRepository;
    private final CurrencyMapper currencyMapper;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public List<CurrencyResponse> findAll() {
        return currencyRepository.findAccessible(currentUser.id())
                .stream()
                .map(currencyMapper::toResponse)
                .toList();
    }

    @Transactional
    public CurrencyResponse create(CurrencyRequest request) {
        UUID userId = currentUser.id();
        String code = request.code().trim().toUpperCase();
        if (!currencyRepository.findAccessibleByCode(code, userId).isEmpty()) {
            throw new ResourceAlreadyExistsException("La devise " + code + " existe déjà");
        }

        Currency currency = Currency.builder()
                .code(code)
                .symbol(request.symbol().trim())
                .name(request.name().trim())
                .owner(currentUser.reference())
                .build();
        Currency saved = currencyRepository.save(currency);
        log.info("A new currency has been created : {}", code);

        return currencyMapper.toResponse(saved);
    }

    @Transactional
    public CurrencyResponse update(Long id, CurrencyRequest request) {
        UUID userId = currentUser.id();
        Currency existing = findOwned(id, userId);

        String code = request.code().trim().toUpperCase();
        boolean conflict = currencyRepository.findAccessibleByCode(code, userId).stream()
                .anyMatch(c -> !c.getId().equals(id));
        if (conflict) {
            throw new ResourceAlreadyExistsException("La devise " + code + " existe déjà");
        }

        existing.setCode(code);
        existing.setName(request.name().trim());
        existing.setSymbol(request.symbol().trim());
        log.info("Currency has been modified : {}", code);

        return currencyMapper.toResponse(existing);
    }

    @Transactional
    public void delete(Long id) {
        UUID userId = currentUser.id();
        Currency currency = findOwned(id, userId);

        if (accountRepository.existsByCurrency_Id(id)) {
            throw new BadRequestException("Cette devise est utilisée par au moins un compte : changez d'abord la devise de ces comptes");
        }
        User user = userRepository.findWithCurrencyById(userId).orElseThrow();
        if (user.getReferenceCurrency() != null && user.getReferenceCurrency().getId().equals(id)) {
            throw new BadRequestException("Cette devise est votre devise de référence : choisissez-en une autre dans votre profil avant de la supprimer");
        }

        exchangeRateRepository.deleteByCurrencyId(id);
        currencyRepository.delete(currency);
        log.info("Currency with id {} has been deleted", id);
    }

    public Currency findAccessible(Long id) {
        return currencyRepository.findAccessibleById(id, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("Devise introuvable"));
    }

    private Currency findOwned(Long id, UUID userId) {
        Currency currency = currencyRepository.findAccessibleById(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Devise introuvable"));
        if (!currency.isCustom()) {
            throw new BadRequestException("Les devises par défaut ne peuvent pas être modifiées");
        }
        return currency;
    }
}
