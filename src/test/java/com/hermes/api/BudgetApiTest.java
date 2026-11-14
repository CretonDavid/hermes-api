package com.hermes.api;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Budget par enveloppes : dépenses couvertes, montants par mois et règles de recouvrement.
 */
class BudgetApiTest extends ApiTestSupport {

    private void envelope(Session s, long categoryId, String amount, String month) throws Exception {
        mockMvc.perform(as(s, post("/budget/envelopes"))
                        .content("{\"categoryId\":" + categoryId + ",\"amount\":" + amount + ",\"month\":\"" + month + "\"}"))
                .andExpect(status().isNoContent());
    }

    private long envelopeId(Session s, String month) throws Exception {
        return ((Number) read(mockMvc.perform(as(s, get("/budget")).param("month", month)).andReturn(),
                "$.envelopes[0].id")).longValue();
    }

    private void amount(Session s, long id, String amount, String month, boolean monthOnly) throws Exception {
        mockMvc.perform(as(s, put("/budget/envelopes/" + id + "/amount"))
                        .content("{\"amount\":" + amount + ",\"month\":\"" + month + "\",\"monthOnly\":" + monthOnly + "}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void envelopesCoverTheirSubcategoriesAndTheRestIsUnbudgeted() throws Exception {
        Session s = newUser();
        long account = account(s, "Courant", accountType(s, "Courant", false), eurId(s), "0");
        envelope(s, categoryId(s, "Alimentation"), "400", "2026-04");
        transaction(s, account, "DEBIT", "100", "Courses", categoryId(s, "Courses"), "2026-04-02");
        transaction(s, account, "DEBIT", "50", "Resto", categoryId(s, "Restaurants"), "2026-04-03");
        // Un remboursement vient en déduction
        transaction(s, account, "CREDIT", "10", "Remboursement resto", categoryId(s, "Restaurants"), "2026-04-05");
        transaction(s, account, "DEBIT", "40", "Essence", categoryId(s, "Carburant"), "2026-04-04");
        transaction(s, account, "DEBIT", "7", "Inconnu", null, "2026-04-06");
        transaction(s, account, "DEBIT", "30", "Mois suivant", categoryId(s, "Courses"), "2026-05-01");

        mockMvc.perform(as(s, get("/budget")).param("month", "2026-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-04"))
                .andExpect(jsonPath("$.currencyCode").value("EUR"))
                .andExpect(jsonPath("$.budgeted").value(400.0))
                .andExpect(jsonPath("$.spent").value(140.0))
                .andExpect(jsonPath("$.unbudgeted").value(47.0))
                .andExpect(jsonPath("$.envelopes.length()").value(1))
                .andExpect(jsonPath("$.envelopes[0].category.name").value("Alimentation"))
                .andExpect(jsonPath("$.envelopes[0].remaining").value(260.0));

        // L'enveloppe n'existe pas avant son premier mois
        mockMvc.perform(as(s, get("/budget")).param("month", "2026-03"))
                .andExpect(jsonPath("$.envelopes.length()").value(0))
                .andExpect(jsonPath("$.budgeted").value(0));
    }

    @Test
    void amountsChangeFromAMonthOrForThatMonthOnly() throws Exception {
        Session s = newUser();
        envelope(s, categoryId(s, "Loisirs"), "150", "2026-01");
        long id = envelopeId(s, "2026-01");

        amount(s, id, "200", "2026-03", false);
        amount(s, id, "500", "2026-12", true);

        mockMvc.perform(as(s, get("/budget")).param("month", "2026-02"))
                .andExpect(jsonPath("$.envelopes[0].amount").value(150.0));
        mockMvc.perform(as(s, get("/budget")).param("month", "2026-06"))
                .andExpect(jsonPath("$.envelopes[0].amount").value(200.0))
                .andExpect(jsonPath("$.envelopes[0].monthOnly").value(false));
        mockMvc.perform(as(s, get("/budget")).param("month", "2026-12"))
                .andExpect(jsonPath("$.envelopes[0].amount").value(500.0))
                .andExpect(jsonPath("$.envelopes[0].usualAmount").value(200.0))
                .andExpect(jsonPath("$.envelopes[0].monthOnly").value(true));

        // Un nouveau montant habituel garde l'exception de décembre
        amount(s, id, "250", "2026-05", false);
        mockMvc.perform(as(s, get("/budget")).param("month", "2026-12"))
                .andExpect(jsonPath("$.envelopes[0].amount").value(500.0))
                .andExpect(jsonPath("$.envelopes[0].usualAmount").value(250.0));

        mockMvc.perform(as(s, delete("/budget/envelopes/" + id + "/amount")).param("month", "2026-12"))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(s, get("/budget")).param("month", "2026-12"))
                .andExpect(jsonPath("$.envelopes[0].amount").value(250.0))
                .andExpect(jsonPath("$.envelopes[0].monthOnly").value(false));
    }

    @Test
    void overlappingOrIncomeEnvelopesAreRejected() throws Exception {
        Session s = newUser();
        long food = categoryId(s, "Alimentation");
        long groceries = categoryId(s, "Courses");
        envelope(s, groceries, "300", "2026-04");

        String body = "{\"categoryId\":%d,\"amount\":100,\"month\":\"2026-04\"}";
        mockMvc.perform(as(s, post("/budget/envelopes")).content(body.formatted(groceries)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(s, post("/budget/envelopes")).content(body.formatted(food)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(s, post("/budget/envelopes")).content(body.formatted(categoryId(s, "Salaire net"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingTheCategoryOrTheEnvelopeRemovesIt() throws Exception {
        Session s = newUser();
        long groceries = categoryId(s, "Courses");
        envelope(s, groceries, "300", "2026-04");
        envelope(s, categoryId(s, "Loisirs"), "100", "2026-04");

        mockMvc.perform(as(s, delete("/categories/" + groceries))).andExpect(status().isNoContent());
        mockMvc.perform(as(s, get("/budget")).param("month", "2026-04"))
                .andExpect(jsonPath("$.envelopes.length()").value(1));

        mockMvc.perform(as(s, delete("/budget/envelopes/" + envelopeId(s, "2026-04")))).andExpect(status().isNoContent());
        mockMvc.perform(as(s, get("/budget")).param("month", "2026-04"))
                .andExpect(jsonPath("$.envelopes.length()").value(0));

        // Le compte utilisateur se supprime avec ses enveloppes
        envelope(s, categoryId(s, "Restaurants"), "80", "2026-04");
        mockMvc.perform(as(s, delete("/users/me")).content("{\"accountNumber\":\"" + s.accountNumber() + "\"}"))
                .andExpect(status().isNoContent());
    }
}
