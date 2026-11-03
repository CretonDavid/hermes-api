package com.untitled.hermesapi.service;

import com.untitled.hermesapi.dto.user.UpdateUserRequest;
import com.untitled.hermesapi.dto.user.UserResponse;
import com.untitled.hermesapi.exception.InvalidCredentialsException;
import com.untitled.hermesapi.exception.ResourceNotFoundException;
import com.untitled.hermesapi.mapper.CurrencyMapper;
import com.untitled.hermesapi.model.Currency;
import com.untitled.hermesapi.model.User;
import com.untitled.hermesapi.repository.*;
import com.untitled.hermesapi.utils.AccountNumberGenerator;
import com.untitled.hermesapi.utils.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    public static final String DEFAULT_REFERENCE_CURRENCY = "EUR";

    private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile("\\d{30}");
    private static final int KEY_LENGTH = 6;

    private final AccountNumberGenerator accountNumberGenerator;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final CurrencyRepository currencyRepository;
    private final CategoryService categoryService;
    private final CurrencyMapper currencyMapper;
    private final CurrentUser currentUser;

    private final TransactionRepository transactionRepository;
    private final ReconciliationRepository reconciliationRepository;
    private final RecurringTransactionRepository recurringTransactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final AccountTypeRepository accountTypeRepository;

    /**
     * Crée un utilisateur et renvoie son numéro de compte en clair. Seul son hash est conservé :
     * le numéro ne pourra jamais être retrouvé.
     */
    @Transactional
    public String createUser() {
        String accountNumber = generateAccountNumber();

        User user = User.builder()
                .lookupKey(extractKey(accountNumber))
                .accountNumberHash(passwordEncoder.encode(accountNumber))
                .createdAt(LocalDateTime.now())
                .enabled(true)
                .referenceCurrency(currencyRepository.findByCodeAndOwnerIsNull(DEFAULT_REFERENCE_CURRENCY).orElse(null))
                .build();

        userRepository.save(user);
        categoryService.createDefaultCategories(user);
        log.info("New user created : {}", user.getId());

        return accountNumber;
    }

    /**
     * Vérifie le numéro de compte complet contre le hash stocké.
     */
    @Transactional(readOnly = true)
    public User authenticate(String accountNumber) {
        if (accountNumber == null || !ACCOUNT_NUMBER_PATTERN.matcher(accountNumber).matches()) {
            throw new InvalidCredentialsException("Numéro de compte invalide");
        }

        User user = userRepository.findByLookupKey(extractKey(accountNumber))
                .orElseThrow(() -> new InvalidCredentialsException("Numéro de compte invalide"));

        if (!passwordEncoder.matches(accountNumber, user.getAccountNumberHash())) {
            throw new InvalidCredentialsException("Numéro de compte invalide");
        }
        return user;
    }

    @Transactional
    public void recordLogin(UUID userId, boolean enable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("Utilisateur introuvable"));
        if (enable) {
            user.setEnabled(true);
        }
        user.setLastLoginAt(LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public UserResponse getMe() {
        return toResponse(findCurrentUser());
    }

    @Transactional
    public UserResponse update(UpdateUserRequest request) {
        User user = findCurrentUser();
        Currency currency = currencyRepository.findAccessibleById(request.referenceCurrencyId(), user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Devise introuvable"));
        user.setReferenceCurrency(currency);
        return toResponse(user);
    }

    @Transactional
    public void disable() {
        User user = findCurrentUser();
        user.setEnabled(false);
        log.info("User {} disabled", user.getId());
    }

    /**
     * Suppression définitive de l'utilisateur et de toutes ses données.
     */
    @Transactional
    public void delete(String accountNumber) {
        UUID userId = currentUser.id();
        User verified = authenticate(accountNumber);
        if (!verified.getId().equals(userId)) {
            throw new InvalidCredentialsException("Numéro de compte invalide");
        }

        transactionRepository.unlinkAllByOwnerId(userId);
        transactionRepository.deleteByOwnerId(userId);
        reconciliationRepository.deleteByOwnerId(userId);
        recurringTransactionRepository.deleteByOwnerId(userId);
        accountRepository.deleteByOwnerId(userId);
        categoryRepository.clearParentsByOwnerId(userId);
        categoryRepository.deleteByOwnerId(userId);
        exchangeRateRepository.deleteByOwnerId(userId);
        accountTypeRepository.deleteByOwnerId(userId);

        User user = userRepository.findById(userId).orElseThrow();
        user.setReferenceCurrency(null);
        userRepository.flush();
        currencyRepository.deleteByOwnerId(userId);
        userRepository.delete(user);
        log.info("User {} permanently deleted", userId);
    }

    public User findCurrentUser() {
        return userRepository.findWithCurrencyById(currentUser.id())
                .orElseThrow(() -> new InvalidCredentialsException("Utilisateur introuvable"));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getCreatedAt(),
                user.getLastLoginAt(),
                currencyMapper.toResponse(user.getReferenceCurrency()));
    }

    private String generateAccountNumber() {
        String accountNumber;
        do {
            accountNumber = accountNumberGenerator.generate();
        } while (userRepository.existsByLookupKey(extractKey(accountNumber)));
        return accountNumber;
    }

    // La clé correspond aux 6 premiers chiffres du numéro de compte
    private String extractKey(String accountNumber) {
        return accountNumber.substring(0, KEY_LENGTH);
    }
}
