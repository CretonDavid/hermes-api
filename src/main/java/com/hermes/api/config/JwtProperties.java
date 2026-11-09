package com.hermes.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param reuseGrace délai pendant lequel un refresh token tout juste remplacé est refusé sans que
 *                   les autres sessions soient révoquées (rafraîchissement simultané de deux onglets)
 */
@ConfigurationProperties(prefix = "application.security.jwt")
public record JwtProperties(
        String secret,
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        @DefaultValue("30s") Duration reuseGrace
) {}
