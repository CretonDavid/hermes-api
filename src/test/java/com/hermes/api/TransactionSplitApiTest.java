package com.hermes.api;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ventilation d'une opération sur plusieurs catégories : saisie, liste filtrée, totaux et statistiques.
 */
class TransactionSplitApiTest extends ApiTestSupport {

    private long split(Session s, long account, String amount, String date, long... categoryAndCents) throws Exception {
        return readId(mockMvc.perform(as(s, post("/transactions")).content(body(account, amount, date, categoryAndCents)))
                .andExpect(status().isCreated()).andReturn());
    }

    private static String body(long account, String amount, String date, long... categoryAndCents) {
        StringBuilder splits = new StringBuilder();
        for (int i = 0; i < categoryAndCents.length; i += 2) {
            if (!splits.isEmpty()) {
                splits.append(',');
            }
            splits.append("{\"categoryId\":").append(categoryAndCents[i])
                    .append(",\"amount\":").append(categoryAndCents[i + 1] / 100.0).append('}');
        }
        return "{\"accountId\":" + account + ",\"direction\":\"DEBIT\",\"amount\":" + amount
                + ",\"label\":\"Supermarché\",\"operationDate\":\"" + date + "\",\"splits\":[" + splits + "]}";
    }

    @Test
    void splitIsSavedAndMustAddUpToTheAmount() throws Exception {
        Session s = newUser();
        long account = account(s, "Courant", accountType(s, "Courant", false), eurId(s), "0");
        long groceries = categoryId(s, "Courses");
        long home = categoryId(s, "Maison & déco");

        long id = split(s, account, "120", "2026-04-02", groceries, 8500, home, 3500);
        mockMvc.perform(as(s, get("/transactions/" + id)))
                .andExpect(jsonPath("$.category").doesNotExist())
                .andExpect(jsonPath("$.splits.length()").value(2))
                .andExpect(jsonPath("$.splits[0].category.name").value("Courses"))
                .andExpect(jsonPath("$.splits[0].amount").value(85.0))
                .andExpect(jsonPath("$.splits[1].amount").value(35.0));

        mockMvc.perform(as(s, post("/transactions")).content(body(account, "120", "2026-04-02", groceries, 8500, home, 3000)))
                .andExpect(status().isBadRequest());

        // Une seule ligne revient à une catégorie simple
        mockMvc.perform(as(s, put("/transactions/" + id)).content(body(account, "120", "2026-04-02", home, 12000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category.name").value("Maison & déco"))
                .andExpect(jsonPath("$.splits.length()").value(0));
    }

    @Test
    void filteredListAndTotalsOnlyCountTheMatchingLines() throws Exception {
        Session s = newUser();
        long account = account(s, "Courant", accountType(s, "Courant", false), eurId(s), "0");
        long groceries = categoryId(s, "Courses");
        long home = categoryId(s, "Maison & déco");
        split(s, account, "120", "2026-04-02", groceries, 8500, home, 3500);
        transaction(s, account, "DEBIT", "10", "Épicerie", groceries, "2026-04-03");
        transaction(s, account, "DEBIT", "7", "Inconnu", null, "2026-04-04");

        // Le parent Alimentation inclut Courses
        String alimentation = String.valueOf(categoryId(s, "Alimentation"));
        mockMvc.perform(as(s, get("/transactions")).param("categoryIds", alimentation).param("sort", "date").param("direction", "asc"))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].amount").value(120.0))
                .andExpect(jsonPath("$.content[0].matchedAmount").value(85.0))
                .andExpect(jsonPath("$.content[1].matchedAmount").doesNotExist());
        mockMvc.perform(as(s, get("/transactions/totals")).param("categoryIds", alimentation))
                .andExpect(jsonPath("$[0].count").value(2))
                .andExpect(jsonPath("$[0].debits").value(95.0));

        // Une opération ventilée n'est pas « non catégorisée »
        mockMvc.perform(as(s, get("/transactions")).param("uncategorized", "true"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].label").value("Inconnu"));
        mockMvc.perform(as(s, get("/transactions/totals")))
                .andExpect(jsonPath("$[0].count").value(3))
                .andExpect(jsonPath("$[0].debits").value(137.0));
    }

    @Test
    void statsSpreadSplitsOverTheirCategories() throws Exception {
        Session s = newUser();
        long account = account(s, "Courant", accountType(s, "Courant", false), eurId(s), "0");
        long groceries = categoryId(s, "Courses");
        long restaurants = categoryId(s, "Restaurants");
        long home = categoryId(s, "Maison & déco");
        split(s, account, "120", "2026-04-02", groceries, 6000, restaurants, 2500, home, 3500);

        String period = "from=2026-04-01&to=2026-04-30";
        mockMvc.perform(as(s, get("/stats/summary?" + period)))
                .andExpect(jsonPath("$.expenses").value(120.0));
        mockMvc.perform(as(s, get("/stats/categories?" + period)))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].name").value("Alimentation"))
                .andExpect(jsonPath("$.items[0].amount").value(85.0))
                .andExpect(jsonPath("$.items[1].amount").value(35.0));
        mockMvc.perform(as(s, get("/stats/category-trend?categoryId=" + restaurants + "&" + period)))
                .andExpect(jsonPath("$.items[0].amount").value(25.0));
        mockMvc.perform(as(s, get("/stats/payees?" + period)))
                .andExpect(jsonPath("$.items[0].amount").value(120.0));
    }

    @Test
    void deletingACategoryOrCategorizingInBulkKeepsSplitsConsistent() throws Exception {
        Session s = newUser();
        long account = account(s, "Courant", accountType(s, "Courant", false), eurId(s), "0");
        long groceries = categoryId(s, "Courses");
        long restaurants = categoryId(s, "Restaurants");
        long home = categoryId(s, "Maison & déco");
        long id = split(s, account, "120", "2026-04-02", groceries, 8500, home, 3500);

        mockMvc.perform(as(s, delete("/categories/" + home))).andExpect(status().isNoContent());
        mockMvc.perform(as(s, get("/transactions/" + id)))
                .andExpect(jsonPath("$.splits.length()").value(2))
                .andExpect(jsonPath("$.splits[1].category").doesNotExist());
        mockMvc.perform(as(s, get("/transactions")).param("uncategorized", "true"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].matchedAmount").value(35.0));

        mockMvc.perform(as(s, patch("/transactions/category"))
                        .content("{\"transactionIds\":[" + id + "],\"categoryId\":" + restaurants + "}"))
                .andExpect(status().isOk());
        mockMvc.perform(as(s, get("/transactions/" + id)))
                .andExpect(jsonPath("$.category.name").value("Restaurants"))
                .andExpect(jsonPath("$.splits.length()").value(0));

        // Un compte bancaire avec des opérations ventilées se supprime
        split(s, account, "20", "2026-04-03", groceries, 1000, restaurants, 1000);
        mockMvc.perform(as(s, delete("/accounts/" + account))).andExpect(status().isNoContent());
    }
}
