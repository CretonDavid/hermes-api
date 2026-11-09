package com.hermes.api.utils;

import com.hermes.api.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite, par adresse IP, les appels aux routes publiques d'authentification : chaque appel coûte
 * un calcul Argon2 et une inscription crée des données. Journalise aussi les échecs de connexion.
 */
@Slf4j
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final String TOO_MANY_REQUESTS_BODY =
            "{\"status\":429,\"code\":\"TOO_MANY_REQUESTS\",\"message\":\"Trop de tentatives : réessayez dans quelques minutes\"}";

    private record Rule(String name, int limit, Duration window) {
    }

    private record Window(long start, int count) {
    }

    private final Map<String, Rule> rules;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    @Autowired
    public AuthRateLimitFilter(RateLimitProperties properties) {
        this(properties, Clock.systemUTC());
    }

    AuthRateLimitFilter(RateLimitProperties properties, Clock clock) {
        this.clock = clock;
        Rule login = new Rule("login", properties.loginPerMinute(), Duration.ofMinutes(1));
        this.rules = Map.of(
                "/auth/login", login,
                "/auth/reactivate", login,
                "/auth/register", new Rule("register", properties.registerPerHour(), Duration.ofHours(1)),
                "/auth/refresh", new Rule("refresh", properties.refreshPerMinute(), Duration.ofMinutes(1)));
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod()) || !rules.containsKey(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain
    ) throws ServletException, IOException {

        Rule rule = rules.get(request.getServletPath());
        String client = request.getRemoteAddr();
        if (!tryAcquire(rule, client)) {
            log.warn("Rate limit '{}' exceeded by {}", rule.name(), client);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(rule.window().toSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(TOO_MANY_REQUESTS_BODY);
            return;
        }

        chain.doFilter(request, response);

        int status = response.getStatus();
        if ("login".equals(rule.name())
                && (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value())) {
            log.warn("Failed authentication on {} from {} (HTTP {})", request.getServletPath(), client, status);
        }
    }

    private boolean tryAcquire(Rule rule, String client) {
        long now = clock.millis();
        long windowMillis = rule.window().toMillis();
        Window window = windows.compute(rule.name() + '|' + client, (key, current) ->
                current == null || now - current.start() >= windowMillis
                        ? new Window(now, 1)
                        : new Window(current.start(), current.count() + 1));
        return window.count() <= rule.limit();
    }

    // Les fenêtres terminées sont oubliées pour que la table ne grossisse pas indéfiniment
    @Scheduled(fixedDelay = 600_000)
    void evictExpiredWindows() {
        long now = clock.millis();
        long longest = rules.values().stream().mapToLong(rule -> rule.window().toMillis()).max().orElse(0);
        windows.values().removeIf(window -> now - window.start() >= longest);
    }
}
