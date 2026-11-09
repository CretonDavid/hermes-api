package com.hermes.api.utils;

import com.hermes.api.exception.InvalidCredentialsException;
import com.hermes.api.model.User;
import com.hermes.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Accès à l'utilisateur authentifié par le JWT de la requête courante.
 */
@Component
@RequiredArgsConstructor
public class CurrentUser {

    private final UserRepository userRepository;

    public UUID id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) {
            throw new InvalidCredentialsException("Authentification requise");
        }
        return userId;
    }

    // Référence JPA sans requête, à utiliser pour rattacher une entité à l'utilisateur
    public User reference() {
        return userRepository.getReferenceById(id());
    }
}
