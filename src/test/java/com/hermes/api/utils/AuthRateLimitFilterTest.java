package com.hermes.api.utils;

import com.hermes.api.config.RateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRateLimitFilterTest {

    private MutableClock clock;
    private AuthRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-09T10:00:00Z"));
        filter = new AuthRateLimitFilter(new RateLimitProperties(2, 1, 3), clock);
    }

    @Test
    void loginAttemptsAreLimitedPerAddressAndWindow() throws Exception {
        assertThat(call("POST", "/auth/login", "10.0.0.1").getStatus()).isEqualTo(200);
        assertThat(call("POST", "/auth/reactivate", "10.0.0.1").getStatus()).isEqualTo(200);

        MockHttpServletResponse blocked = call("POST", "/auth/login", "10.0.0.1");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
        assertThat(blocked.getContentAsString()).contains("TOO_MANY_REQUESTS");

        // Une autre adresse n'est pas concernée
        assertThat(call("POST", "/auth/login", "10.0.0.2").getStatus()).isEqualTo(200);

        // La fenêtre suivante repart de zéro
        clock.advance(Duration.ofMinutes(1));
        assertThat(call("POST", "/auth/login", "10.0.0.1").getStatus()).isEqualTo(200);
    }

    @Test
    void eachRouteHasItsOwnLimit() throws Exception {
        assertThat(call("POST", "/auth/register", "10.0.0.1").getStatus()).isEqualTo(200);
        assertThat(call("POST", "/auth/register", "10.0.0.1").getStatus()).isEqualTo(429);
        assertThat(call("POST", "/auth/refresh", "10.0.0.1").getStatus()).isEqualTo(200);
        assertThat(call("POST", "/auth/login", "10.0.0.1").getStatus()).isEqualTo(200);
    }

    @Test
    void otherRequestsAreNotLimited() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(call("GET", "/accounts", "10.0.0.1").getStatus()).isEqualTo(200);
            assertThat(call("POST", "/auth/logout", "10.0.0.1").getStatus()).isEqualTo(200);
        }
    }

    @Test
    void expiredWindowsAreEvicted() throws Exception {
        call("POST", "/auth/register", "10.0.0.1");
        clock.advance(Duration.ofHours(1));
        filter.evictExpiredWindows();
        assertThat(call("POST", "/auth/register", "10.0.0.1").getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse call(String method, String path, String address) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.setRemoteAddr(address);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
