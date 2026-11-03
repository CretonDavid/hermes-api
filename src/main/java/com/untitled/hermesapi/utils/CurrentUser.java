package com.untitled.hermesapi.utils;

import com.untitled.hermesapi.exception.InvalidCredentialsException;
import com.untitled.hermesapi.model.User;
import com.untitled.hermesapi.repository.UserRepository;
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
