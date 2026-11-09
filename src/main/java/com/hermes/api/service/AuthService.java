package com.hermes.api.service;

import com.hermes.api.config.JwtProperties;
import com.hermes.api.dto.auth.LoginRequest;
import com.hermes.api.dto.auth.LoginResponse;
import com.hermes.api.dto.auth.RefreshRequest;
import com.hermes.api.dto.auth.RegisterResponse;
import com.hermes.api.exception.AccountDisabledException;
import com.hermes.api.exception.InvalidCredentialsException;
import com.hermes.api.model.RefreshToken;
import com.hermes.api.model.User;
import com.hermes.api.repository.RefreshTokenRepository;
import com.hermes.api.repository.UserRepository;
import com.hermes.api.service.JwtService.IssuedToken;
import com.hermes.api.service.JwtService.TokenType;
import com.hermes.api.service.JwtService.VerifiedToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String SESSION_EXPIRED = "Session expirée";

    private final UserService userService;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final RecurringTransactionService recurringTransactionService;
    private final JwtProperties jwtProperties;

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

    /**
     * Échange un refresh token contre une nouvelle paire de jetons. L'ancien est marqué comme
     * remplacé : s'il est présenté de nouveau plus tard, il a été volé et toutes les sessions de
     * l'utilisateur sont révoquées.
     */
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public LoginResponse refresh(RefreshRequest request) {
        VerifiedToken verified = jwtService.verify(request.refreshToken(), TokenType.REFRESH)
                .orElseThrow(() -> new InvalidCredentialsException(SESSION_EXPIRED));
        RefreshToken stored = refreshTokenRepository.findById(verified.tokenId())
                .orElseThrow(() -> new InvalidCredentialsException(SESSION_EXPIRED));

        Instant now = Instant.now();
        if (stored.getRotatedAt() != null) {
            // Deux onglets peuvent rafraîchir la session au même moment : la réutilisation d'un jeton
            // tout juste remplacé est refusée sans couper les autres sessions
            if (stored.getRotatedAt().plus(jwtProperties.reuseGrace()).isBefore(now)) {
                int revoked = refreshTokenRepository.deleteByUserId(verified.userId());
                log.warn("Refresh token reuse detected for user {}: {} sessions revoked", verified.userId(), revoked);
            }
            throw new InvalidCredentialsException(SESSION_EXPIRED);
        }

        User user = userRepository.findById(verified.userId())
                .orElseThrow(() -> new InvalidCredentialsException(SESSION_EXPIRED));
        if (!user.isEnabled()) {
            throw new AccountDisabledException();
        }
        stored.setRotatedAt(now);
        return issueTokens(user.getId());
    }

    /**
     * Ferme la session portée par ce refresh token. Sans effet si le jeton est invalide ou déjà révoqué.
     */
    @Transactional
    public void logout(RefreshRequest request) {
        jwtService.verify(request.refreshToken(), TokenType.REFRESH)
                .ifPresent(token -> refreshTokenRepository.deleteById(token.tokenId()));
    }

    @Transactional
    @Scheduled(cron = "${application.security.refresh-token-cleanup-cron:0 30 3 * * *}")
    public void purgeExpiredTokens() {
        int purged = refreshTokenRepository.deleteExpired(Instant.now());
        if (purged > 0) {
            log.info("{} expired refresh tokens purged", purged);
        }
    }

    private LoginResponse openSession(UUID userId, boolean enable) {
        userService.recordLogin(userId, enable);
        try {
            recurringTransactionService.generateDueForUser(userId);
        } catch (RuntimeException exception) {
            // La connexion ne doit pas échouer à cause de la génération des récurrences
            log.error("Failed to generate recurring transactions for user {}", userId, exception);
        }
        return issueTokens(userId);
    }

    private LoginResponse issueTokens(UUID userId) {
        IssuedToken refresh = jwtService.generateRefreshToken(userId);
        refreshTokenRepository.save(RefreshToken.builder()
                .id(refresh.tokenId())
                .user(userRepository.getReferenceById(userId))
                .expiresAt(refresh.expiresAt())
                .build());
        return new LoginResponse(jwtService.generateAccessToken(userId).value(), refresh.value());
    }
}
