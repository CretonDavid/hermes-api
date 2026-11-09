package com.hermes.api;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Synchronisation des taux BCE, contre un faux fournisseur reproduisant l'API Frankfurter.
 */
class ExchangeRateSyncApiTest extends ApiTestSupport {

    private static final HttpServer PROVIDER = startProvider();

    @DynamicPropertySource
    static void providerUrl(DynamicPropertyRegistry registry) {
        registry.add("application.exchange-rates.provider-url",
                () -> "http://localhost:" + PROVIDER.getAddress().getPort());
    }

    @AfterAll
    static void stopProvider() {
        PROVIDER.stop(0);
    }

    @Test
    void latestAndHistoricalRatesAreImportedForAccountCurrencies() throws Exception {
        Session s = newUser();
        long type = accountType(s, "Courant", false);
        account(s, "Euros", type, eurId(s), "0");
        account(s, "Dollars", type, currencyId(s, "USD"), "0");
        account(s, "Francs CFA", type, currencyId(s, "XOF"), "0");

        mockMvc.perform(as(s, post("/exchange-rates/sync")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.unsupportedCurrencies", contains("XOF")));

        mockMvc.perform(as(s, post("/exchange-rates/sync")).param("from", "2026-01-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(2));

        // Une nouvelle synchronisation met à jour les taux existants au lieu de les dupliquer
        mockMvc.perform(as(s, post("/exchange-rates/sync")).param("from", "2026-01-01"))
                .andExpect(jsonPath("$.imported").value(2));
        mockMvc.perform(as(s, get("/exchange-rates")))
                .andExpect(jsonPath("$.length()").value(3));
        mockMvc.perform(as(s, get("/exchange-rates")).param("latest", "true"))
                .andExpect(jsonPath("$[0].date").value("2026-11-06"))
                .andExpect(jsonPath("$[0].rate").value(1.0812));
    }

    @Test
    void referenceCurrencyMustBeCoveredByTheProvider() throws Exception {
        Session s = newUser();
        long type = accountType(s, "Courant", false);
        long xof = currencyId(s, "XOF");
        account(s, "Dollars", type, currencyId(s, "USD"), "0");
        mockMvc.perform(as(s, patch("/users/me"))
                        .content("{\"referenceCurrencyId\":" + xof + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(as(s, post("/exchange-rates/sync")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La devise XOF n'est pas couverte par la BCE"));
    }

    private static HttpServer startProvider() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                String body;
                if (path.equals("/currencies")) {
                    body = "{\"EUR\":\"Euro\",\"USD\":\"US Dollar\",\"GBP\":\"British Pound\"}";
                } else if (path.equals("/latest")) {
                    body = "{\"base\":\"EUR\",\"date\":\"2026-11-06\",\"rates\":{\"USD\":1.0812}}";
                } else {
                    body = "{\"base\":\"EUR\",\"rates\":{\"2026-01-02\":{\"USD\":1.0350},\"2026-01-05\":{\"USD\":1.0401}}}";
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
