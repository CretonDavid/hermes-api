package com.hermes.api;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Types de compte, devises, taux de change, catégories et profil : règles de gestion et erreurs.
 */
class ReferenceDataApiTest extends ApiTestSupport {

    @Test
    void accountTypesAreUniqueAndCannotBeDeletedWhileUsed() throws Exception {
        Session s = newUser();
        long courant = accountType(s, "Courant", false);
        long livret = accountType(s, "Livret", true);

        mockMvc.perform(as(s, post("/account-types")).content("{\"name\":\"courant\",\"savings\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_EXISTS"));
        mockMvc.perform(as(s, put("/account-types/" + livret)).content("{\"name\":\"Courant\",\"savings\":true}"))
                .andExpect(status().isConflict());
        mockMvc.perform(as(s, put("/account-types/" + livret)).content("{\"name\":\"Épargne\",\"savings\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Épargne"));

        account(s, "Compte", courant, eurId(s), "0");
        mockMvc.perform(as(s, delete("/account-types/" + courant)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("utilisé")));
        mockMvc.perform(as(s, delete("/account-types/" + livret))).andExpect(status().isNoContent());
        mockMvc.perform(as(s, get("/account-types")))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void customCurrenciesFollowTheirLifecycleRules() throws Exception {
        Session s = newUser();
        long eur = eurId(s);

        mockMvc.perform(as(s, put("/currencies/" + eur)).content("{\"code\":\"EUR\",\"symbol\":\"E\",\"name\":\"Euro\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(s, post("/currencies")).content("{\"code\":\"usd\",\"symbol\":\"$\",\"name\":\"Doublon\"}"))
                .andExpect(status().isConflict());

        long btc = readId(mockMvc.perform(as(s, post("/currencies")).content("{\"code\":\"btc\",\"symbol\":\"₿\",\"name\":\"Bitcoin\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("BTC"))
                .andExpect(jsonPath("$.custom").value(true))
                .andReturn());
        mockMvc.perform(as(s, put("/currencies/" + btc)).content("{\"code\":\"XBT\",\"symbol\":\"₿\",\"name\":\"Bitcoin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("XBT"));

        // Devise de référence : non supprimable tant qu'elle est choisie dans le profil
        mockMvc.perform(as(s, patch("/users/me")).content("{\"referenceCurrencyId\":" + btc + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceCurrency.code").value("XBT"));
        mockMvc.perform(as(s, delete("/currencies/" + btc))).andExpect(status().isBadRequest());
        mockMvc.perform(as(s, patch("/users/me")).content("{\"referenceCurrencyId\":" + eur + "}"))
                .andExpect(status().isOk());

        // Devise d'un compte bancaire : non supprimable non plus
        long account = account(s, "Portefeuille", accountType(s, "Crypto", true), btc, "0");
        mockMvc.perform(as(s, delete("/currencies/" + btc))).andExpect(status().isBadRequest());
        mockMvc.perform(as(s, delete("/accounts/" + account))).andExpect(status().isNoContent());
        mockMvc.perform(as(s, delete("/currencies/" + btc))).andExpect(status().isNoContent());

        // Une devise personnelle n'est visible que de son propriétaire
        Session other = newUser();
        mockMvc.perform(as(other, patch("/users/me")).content("{\"referenceCurrencyId\":" + btc + "}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void exchangeRatesAreUniquePerPairAndDate() throws Exception {
        Session s = newUser();
        long eur = eurId(s);
        long usd = currencyId(s, "USD");

        long rate = readId(mockMvc.perform(as(s, post("/exchange-rates")).content(rate(eur, usd, "1.08", "2026-01-02")))
                .andExpect(status().isCreated()).andReturn());
        mockMvc.perform(as(s, post("/exchange-rates")).content(rate(eur, usd, "1.09", "2026-01-02")))
                .andExpect(status().isConflict());
        mockMvc.perform(as(s, post("/exchange-rates")).content(rate(eur, eur, "1", "2026-01-02")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(s, post("/exchange-rates")).content(rate(eur, usd, "1.10", "2026-02-02")))
                .andExpect(status().isCreated());
        long third = readId(mockMvc.perform(as(s, post("/exchange-rates")).content(rate(eur, usd, "1.12", "2026-03-02")))
                .andExpect(status().isCreated()).andReturn());

        mockMvc.perform(as(s, put("/exchange-rates/" + third)).content(rate(eur, usd, "1.11", "2026-02-02")))
                .andExpect(status().isConflict());
        mockMvc.perform(as(s, put("/exchange-rates/" + rate)).content(rate(eur, usd, "1.07", "2026-01-03")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value(1.07));

        mockMvc.perform(as(s, get("/exchange-rates")).param("currencyId", String.valueOf(usd)))
                .andExpect(jsonPath("$.length()").value(3));
        mockMvc.perform(as(s, get("/exchange-rates")).param("latest", "true"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].date").value("2026-03-02"));

        mockMvc.perform(as(s, delete("/exchange-rates/" + third))).andExpect(status().isNoContent());
        mockMvc.perform(as(s, delete("/exchange-rates/" + third))).andExpect(status().isNotFound());

        // Synchronisation : la date de début ne peut pas être future
        mockMvc.perform(as(s, post("/exchange-rates/sync")).param("from", "2999-01-01"))
                .andExpect(status().isBadRequest());
        // Sans compte en devise étrangère, il n'y a rien à synchroniser
        mockMvc.perform(as(s, post("/exchange-rates/sync")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(0));
    }

    @Test
    void categoriesKeepTheirTreeConsistent() throws Exception {
        Session s = newUser();
        long parent = readId(mockMvc.perform(as(s, post("/categories"))
                        .content("{\"name\":\"Loisirs perso\",\"type\":\"EXPENSE\",\"color\":\"#112233\"}"))
                .andExpect(status().isCreated()).andReturn());
        long child = readId(mockMvc.perform(as(s, post("/categories"))
                        .content("{\"name\":\"Jeux\",\"type\":\"INCOME\",\"parentId\":" + parent + "}"))
                .andExpect(status().isCreated())
                // Une sous-catégorie prend le type de son parent
                .andExpect(jsonPath("$.type").value("EXPENSE"))
                .andReturn());

        // Pas de cycle : un parent ne peut pas être rangé sous sa sous-catégorie
        mockMvc.perform(as(s, put("/categories/" + parent))
                        .content("{\"name\":\"Loisirs perso\",\"type\":\"EXPENSE\",\"parentId\":" + child + "}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(s, put("/categories/" + parent))
                        .content("{\"name\":\"Loisirs perso\",\"type\":\"EXPENSE\",\"parentId\":" + parent + "}"))
                .andExpect(status().isBadRequest());

        // Le changement de type d'un parent s'applique à ses sous-catégories
        mockMvc.perform(as(s, put("/categories/" + parent)).content("{\"name\":\"Loisirs perso\",\"type\":\"INCOME\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(as(s, get("/categories")))
                .andExpect(jsonPath("$[?(@.id == " + child + ")].type", contains("INCOME")));

        mockMvc.perform(as(s, delete("/categories/" + parent)).param("replacementId", String.valueOf(parent)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(s, delete("/categories/" + parent))).andExpect(status().isNoContent());
        // La sous-catégorie remonte d'un niveau
        mockMvc.perform(as(s, get("/categories")))
                .andExpect(jsonPath("$[?(@.id == " + child + ")].parentId", contains(nullValue())));

        mockMvc.perform(as(s, post("/categories")).content("{\"name\":\"Couleur\",\"type\":\"EXPENSE\",\"color\":\"rouge\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.color").exists());
    }

    @Test
    void malformedRequestsAreRejectedWithoutDetails() throws Exception {
        Session s = newUser();
        mockMvc.perform(as(s, post("/accounts")).content("{pas du json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mockMvc.perform(as(s, get("/accounts/abc")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(s, get("/accounts/999999")))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(s, delete("/stats/summary")))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(as(s, get("/inexistant")))
                .andExpect(status().isNotFound());
    }

    private static String rate(long base, long quote, String value, String date) {
        return "{\"baseCurrencyId\":" + base + ",\"quoteCurrencyId\":" + quote + ",\"rate\":" + value
                + ",\"date\":\"" + date + "\"}";
    }
}
