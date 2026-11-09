package com.hermes.api;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recherche, totaux et suggestions de tiers, ainsi que les erreurs d'import.
 */
class TransactionQueriesApiTest extends ApiTestSupport {

    @Test
    void searchTreatsWildcardsLiterally() throws Exception {
        Session s = newUser();
        long account = account(s, "Courant", accountType(s, "Courant", false), eurId(s), "0");
        transaction(s, account, "DEBIT", "10", "Remise 50% boulangerie", null, "2026-01-05");
        transaction(s, account, "DEBIT", "20", "Remise 50 euros", null, "2026-01-06");
        transaction(s, account, "DEBIT", "30", "Code_promo", null, "2026-01-07");
        transaction(s, account, "DEBIT", "40", "Codexpromo", null, "2026-01-08");

        mockMvc.perform(as(s, get("/transactions")).param("search", "50%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].label").value("Remise 50% boulangerie"));
        mockMvc.perform(as(s, get("/transactions")).param("search", "code_"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].label").value("Code_promo"));
    }

    @Test
    void totalsAreComputedPerCurrencyFromFilteredTransactions() throws Exception {
        Session s = newUser();
        long type = accountType(s, "Courant", false);
        long eur = account(s, "Euros", type, eurId(s), "0");
        long usd = account(s, "Dollars", type, currencyId(s, "USD"), "0");
        transaction(s, eur, "CREDIT", "100.50", "Salaire", null, "2026-02-01");
        transaction(s, eur, "DEBIT", "40.25", "Courses", null, "2026-02-03");
        transaction(s, eur, "DEBIT", "9.99", "Hors période", null, "2026-03-01");
        transaction(s, usd, "DEBIT", "12", "Café", null, "2026-02-04");

        mockMvc.perform(as(s, get("/transactions/totals")).param("from", "2026-02-01").param("to", "2026-02-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].currencyCode").value("EUR"))
                .andExpect(jsonPath("$[0].count").value(2))
                .andExpect(jsonPath("$[0].credits").value(100.50))
                .andExpect(jsonPath("$[0].debits").value(40.25))
                .andExpect(jsonPath("$[0].net").value(60.25))
                .andExpect(jsonPath("$[1].currencyCode").value("USD"))
                .andExpect(jsonPath("$[1].net").value(-12.0));

        mockMvc.perform(as(s, get("/transactions/totals")).param("search", "aucune opération"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void payeeSuggestionsCarryTheLastCategoryUsed() throws Exception {
        Session s = newUser();
        long account = account(s, "Courant", accountType(s, "Courant", false), eurId(s), "0");
        long groceries = categoryId(s, "Courses");
        long restaurant = categoryId(s, "Restaurants");
        payeeTransaction(s, account, "Carrefour", groceries, "2026-01-02");
        payeeTransaction(s, account, "CARREFOUR", restaurant, "2026-01-10");
        payeeTransaction(s, account, "Carrefour", null, "2026-01-12");
        payeeTransaction(s, account, "Boulangerie", null, "2026-01-03");

        mockMvc.perform(as(s, get("/transactions/payees")).param("q", "carre"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].payee").value("Carrefour"))
                .andExpect(jsonPath("$[0].usage").value(2))
                .andExpect(jsonPath("$[0].lastCategoryId").value((int) restaurant));
        mockMvc.perform(as(s, get("/transactions/payees")).param("q", "%"))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(as(s, get("/transactions/payees")).param("q", "boul"))
                .andExpect(jsonPath("$[0].lastCategoryId").value(nullValue()));
    }

    @Test
    void ofxRowWithAnUnreadableAmountIsReportedInsteadOfFailing() throws Exception {
        Session s = newUser();
        long account = account(s, "Courant", accountType(s, "Courant", false), eurId(s), "0");
        String ofx = """
                OFXHEADER:100
                <OFX><BANKTRANLIST>
                <STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20260103<TRNAMT>douze<FITID>X1<NAME>ILLISIBLE</STMTTRN>
                <STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20260104<TRNAMT>-12.00<FITID>X2<NAME>Tom &amp; Jerry &amp;lt;3</STMTTRN>
                </BANKTRANLIST></OFX>
                """;
        MockMultipartFile file = new MockMultipartFile("file", "releve.ofx", "application/x-ofx",
                ofx.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/imports/ofx/preview").file(file)
                        .header("Authorization", s.bearer())
                        .param("accountId", String.valueOf(account)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.rows[0].label").value("Tom & Jerry &lt;3"))
                .andExpect(jsonPath("$.warnings[0]", containsString("ILLISIBLE")));
    }

    @Test
    void importRejectsAmountsWithMoreThanTwoDecimals() throws Exception {
        Session s = newUser();
        long account = account(s, "Courant", accountType(s, "Courant", false), eurId(s), "0");

        mockMvc.perform(as(s, post("/imports/confirm")).content("""
                        {"accountId": %d, "rows": [{"operationDate": "2026-01-02", "label": "Test",
                         "amount": 12.345, "direction": "DEBIT"}]}
                        """.formatted(account)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private void payeeTransaction(Session s, long account, String payee, Long category, String date) throws Exception {
        mockMvc.perform(as(s, post("/transactions")).content("""
                        {"accountId": %d, "direction": "DEBIT", "amount": 5, "label": "Achat", "payee": "%s",
                         "categoryId": %s, "operationDate": "%s"}
                        """.formatted(account, payee, category, date)))
                .andExpect(status().isCreated());
    }
}
