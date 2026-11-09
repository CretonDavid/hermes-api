package com.hermes.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nombre maximal d'appels aux routes publiques d'authentification, par adresse IP.
 */
@ConfigurationProperties(prefix = "application.security.rate-limit")
public record RateLimitProperties(
        int loginPerMinute,
        int registerPerHour,
        int refreshPerMinute
) {}
