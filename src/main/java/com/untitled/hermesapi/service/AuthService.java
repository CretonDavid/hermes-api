package com.untitled.hermesapi.service;

import com.untitled.hermesapi.dto.auth.LoginRequest;
import com.untitled.hermesapi.dto.auth.LoginResponse;
import com.untitled.hermesapi.dto.auth.RefreshRequest;
import com.untitled.hermesapi.dto.auth.RegisterResponse;
import com.untitled.hermesapi.exception.AccountDisabledException;
import com.untitled.hermesapi.exception.InvalidCredentialsException;
import com.untitled.hermesapi.model.User;
import com.untitled.hermesapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RecurringTransactionService recurringTransactionService;

    // Le numéro de compte est le seul moyen d'accéder au compte.
    // Il doit être conservé en lieu sûr : il ne peut pas être récupéré.
    public RegisterResponse register() {
        return new RegisterResponse(userService.createUser());
    }

    public LoginResponse login(LoginRequest request) {
        User user = userService.authenticate(request.accountNumber());
        if (!user.isEnabled()) {
            throw new AccountDisabledException();
        }
        return openSession(user.getId(), false);
    }

    public LoginResponse reactivate(LoginRequest request) {
        User user = userService.authenticate(request.accountNumber());
        log.info("User {} reactivated", user.getId());
        return openSession(user.getId(), true);
    }

    public LoginResponse refresh(RefreshRequest request) {
        String token = request.refreshToken();
        if (!jwtService.isValid(token) || !jwtService.isRefreshToken(token)) {
            throw new InvalidCredentialsException("Session expirée");
        }

        User user = userRepository.findById(jwtService.extractUserId(token))
                .orElseThrow(() -> new InvalidCredentialsException("Session expirée"));
        if (!user.isEnabled()) {
            throw new AccountDisabledException();
        }
        return tokens(user.getId());
    }

    private LoginResponse openSession(UUID userId, boolean enable) {
        userService.recordLogin(userId, enable);
        try {
            recurringTransactionService.generateDueForUser(userId);
        } catch (RuntimeException exception) {
            // La connexion ne doit pas échouer à cause de la génération des récurrences
            log.error("Failed to generate recurring transactions for user {}", userId, exception);
        }
        return tokens(userId);
    }

    private LoginResponse tokens(UUID userId) {
        return new LoginResponse(jwtService.generateAccessToken(userId), jwtService.generateRefreshToken(userId));
    }
}
