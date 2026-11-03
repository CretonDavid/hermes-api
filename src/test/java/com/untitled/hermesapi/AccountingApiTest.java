package com.untitled.hermesapi;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountingApiTest extends ApiTestSupport {

    @Test
    void balanceIsComputedFromInitialBalanceAndTransactions() throws Exception {
        Session s = newUser();
        long type = accountType(s, "Courant", false);
        long account = account(s, "Compte courant", type, eurId(s), "1000.00");

        transaction(s, account, "DEBIT", "45.30", "Courses", categoryId(s, "Courses"), "2026-01-05");
        transaction(s, account, "CREDIT", "2500", "Salaire", categoryId(s, "Salaire net"), "2026-01-28");
        // Opération future : exclue du solde à date, incluse dans le prévisionnel
        transaction(s, account, "DEBIT", "100", "Futur", null, LocalDate.now().plusDays(10).toString());

        mockMvc.perform(as(s, get("/accounts/" + account)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(3454.70))
                .andExpect(jsonPath("$.forecastBalance").value(3354.70))
                .andExpect(jsonPath("$.clearedBalance").value(1000.00));
    }

    @Test
    void usersCannotSeeEachOtherData() throws Exception {
        Session alice = newUser();
        Session bob = newUser();
        long type = accountType(alice, "Courant", false);
        long account = account(alice, "Alice", type, eurId(alice), "0");
        long tx = transaction(alice, account, "DEBIT", "10", "Test", null, "2026-01-01");

        mockMvc.perform(as(bob, get("/accounts"))).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(as(bob, get("/accounts/" + account))).andExpect(status().isNotFound());
        mockMvc.perform(as(bob, get("/transactions/" + tx))).andExpect(status().isNotFound());
        mockMvc.perform(as(bob, delete("/accounts/" + account))).andExpect(status().isNotFound());
        mockMvc.perform(as(bob, get("/transactions"))).andExpect(jsonPath("$.totalElements").value(0));
        // Bob ne peut pas utiliser le type de compte d'Alice
        mockMvc.perform(as(bob, post("/accounts"))
                        .content("{\"name\":\"X\",\"accountTypeId\":" + type + ",\"currencyId\":" + eurId(bob) + "}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void validationErrorsAreReported() throws Exception {
        Session s = newUser();
        mockMvc.perform(as(s, post("/transactions")).content("{\"amount\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.accountId").exists())
                .andExpect(jsonPath("$.fieldErrors.amount").exists());
    }

    @Test
    void transferCreatesTwoLinkedTransactionsAndCanBeDeletedAtOnce() throws Exception {
        Session s = newUser();
        long checking = account(s, "Courant", accountType(s, "Courant", false), eurId(s), "500");
        long savings = account(s, "Livret A", accountType(s, "Livret", true), eurId(s), "0");

        MvcResult created = mockMvc.perform(as(s, post("/transfers"))
                        .content("{\"fromAccountId\":" + checking + ",\"toAccountId\":" + savings
                                + ",\"amount\":200,\"operationDate\":\"2026-02-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.debit.label").value("Virement vers Livret A"))
                .andExpect(jsonPath("$.credit.transfer.accountId").value((int) checking))
                .andReturn();

        mockMvc.perform(as(s, get("/accounts/" + checking))).andExpect(jsonPath("$.balance").value(300.0));
        mockMvc.perform(as(s, get("/accounts/" + savings))).andExpect(jsonPath("$.balance").value(200.0));

        // Les virements sont exclus des dépenses mais comptés en épargne
        mockMvc.perform(as(s, get("/stats/summary?from=2026-02-01&to=2026-02-28")))
                .andExpect(jsonPath("$.expenses").value(0.0))
                .andExpect(jsonPath("$.savings").value(200.0));

        Number debitId = read(created, "$.debit.id");
        mockMvc.perform(as(s, put("/transactions/" + debitId))
                        .content("{\"accountId\":" + checking + ",\"direction\":\"DEBIT\",\"amount\":1,\"label\":\"x\",\"operationDate\":\"2026-02-01\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(as(s, put("/transfers/" + debitId))
                        .content("{\"fromAccountId\":" + checking + ",\"toAccountId\":" + savings
                                + ",\"amount\":150,\"operationDate\":\"2026-02-02\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credit.amount").value(150));

        mockMvc.perform(as(s, delete("/transactions/" + debitId))).andExpect(status().isNoContent());
        mockMvc.perform(as(s, get("/transactions?accountIds=" + savings))).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void transferBetweenCurrenciesUsesTargetAmountOrExchangeRate() throws Exception {
        Session s = newUser();
        long type = accountType(s, "Courant", false);
        long eur = account(s, "EUR", type, eurId(s), "0");
        long usd = account(s, "USD", type, currencyId(s, "USD"), "0");

        mockMvc.perform(as(s, post("/transfers"))
                        .content("{\"fromAccountId\":" + eur + ",\"toAccountId\":" + usd + ",\"amount\":100,\"operationDate\":\"2026-03-01\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(as(s, post("/exchange-rates"))
                        .content("{\"baseCurrencyId\":" + eurId(s) + ",\"quoteCurrencyId\":" + currencyId(s, "USD")
                                + ",\"rate\":1.10,\"date\":\"2026-01-01\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(as(s, post("/transfers"))
                        .content("{\"fromAccountId\":" + eur + ",\"toAccountId\":" + usd + ",\"amount\":100,\"operationDate\":\"2026-03-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.credit.amount").value(110.0));

        // Patrimoine converti en EUR : -100 EUR + 110 USD / 1.10 = 0
        mockMvc.perform(as(s, get("/stats/summary"))).andExpect(jsonPath("$.netWorth").value(0.0));
    }

    @Test
    void reconciliationLocksClearedTransactions() throws Exception {
        Session s = newUser();
        long account = account(s, "Courant", accountType(s, "Courant", false), eurId(s), "100");
        long t1 = transaction(s, account, "DEBIT", "30", "A", null, "2026-01-02");
        long t2 = transaction(s, account, "DEBIT", "20", "B", null, "2026-01-03");
        transaction(s, account, "DEBIT", "5", "Non pointée", null, "2026-01-04");

        for (long id : List.of(t1, t2)) {
            mockMvc.perform(as(s, patch("/transactions/" + id + "/status")).content("{\"status\":\"CLEARED\"}"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(as(s, get("/accounts/" + account + "/reconciliation-status")))
                .andExpect(jsonPath("$.clearedBalance").value(50.0))
                .andExpect(jsonPath("$.clearedCount").value(2));

        mockMvc.perform(as(s, post("/accounts/" + account + "/reconciliations"))
                        .content("{\"statementDate\":\"2026-01-31\",\"statementBalance\":45}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("écart")));

        MvcResult reconciliation = mockMvc.perform(as(s, post("/accounts/" + account + "/reconciliations"))
                        .content("{\"statementDate\":\"2026-01-31\",\"statementBalance\":50}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionCount").value(2))
                .andReturn();

        mockMvc.perform(as(s, get("/transactions/" + t1))).andExpect(jsonPath("$.status").value("RECONCILED"));
        mockMvc.perform(as(s, delete("/transactions/" + t1))).andExpect(status().isBadRequest());
        mockMvc.perform(as(s, put("/transactions/" + t1))
                        .content("{\"accountId\":" + account + ",\"direction\":\"DEBIT\",\"amount\":31,\"label\":\"A\",\"operationDate\":\"2026-01-02\"}"))
                .andExpect(status().isBadRequest());
        // Le libellé reste modifiable
        mockMvc.perform(as(s, put("/transactions/" + t1))
                        .content("{\"accountId\":" + account + ",\"direction\":\"DEBIT\",\"amount\":30,\"label\":\"A modifié\",\"operationDate\":\"2026-01-02\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(as(s, delete("/accounts/" + account + "/reconciliations/" + readId(reconciliation))))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(s, get("/transactions/" + t1))).andExpect(jsonPath("$.status").value("CLEARED"));
    }

    @Test
    void recurringTransactionsGenerateDueOccurrences() throws Exception {
        Session s = newUser();
        long account = account(s, "Courant", accountType(s, "Courant", false), eurId(s), "0");
        LocalDate start = LocalDate.now().minusMonths(2);

        MvcResult created = mockMvc.perform(as(s, post("/recurring-transactions"))
                        .content("{\"accountId\":" + account + ",\"direction\":\"DEBIT\",\"amount\":800,\"label\":\"Loyer\","
                                + "\"categoryId\":" + categoryId(s, "Loyer") + ",\"frequency\":\"MONTHLY\",\"interval\":1,"
                                + "\"startDate\":\"" + start + "\",\"active\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.occurrenceCount").value(3))
                .andExpect(jsonPath("$.nextDate").value(start.plusMonths(3).toString()))
                .andReturn();

        mockMvc.perform(as(s, get("/transactions?accountIds=" + account)))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].recurringTransactionId").value((int) readId(created)));

        mockMvc.perform(as(s, get("/recurring-transactions/upcoming?days=95")))
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(3)));

        // Changer le planning ne recrée pas les occurrences passées
        mockMvc.perform(as(s, put("/recurring-transactions/" + readId(created)))
                        .content("{\"accountId\":" + account + ",\"direction\":\"DEBIT\",\"amount\":850,\"label\":\"Loyer\","
                                + "\"frequency\":\"MONTHLY\",\"interval\":1,\"startDate\":\"" + start.plusDays(1) + "\",\"active\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(as(s, get("/transactions?accountIds=" + account))).andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void csvImportPreviewsDetectsDuplicatesAndSuggestsCategories() throws Exception {
        Session s = newUser();
        long account = account(s, "Courant", accountType(s, "Courant", false), eurId(s), "0");
        long groceries = categoryId(s, "Courses");
        transaction(s, account, "DEBIT", "12.30", "CB CARREFOUR 02/01", groceries, "2026-01-02");

        String csv = """
                Relevé de compte;;;;
                Date;Libellé;Débit;Crédit;
                02/01/2026;CB CARREFOUR 02/01;12,30;;
                05/01/2026;CB CARREFOUR 05/01;45,10;;
                10/01/2026;VIR SALAIRE ACME;;2 500,00;
                bad;line;;;
                """;
        MockMultipartFile file = new MockMultipartFile("file", "releve.csv", "text/csv",
                csv.getBytes(Charset.forName("windows-1252")));

        mockMvc.perform(multipart("/imports/csv/inspect").file(file).header("Authorization", s.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.separator").value(";"))
                .andExpect(jsonPath("$.encoding").value("windows-1252"))
                .andExpect(jsonPath("$.rows[0][0]").value("Relevé de compte"));

        MvcResult preview = mockMvc.perform(multipart("/imports/csv/preview").file(file)
                        .header("Authorization", s.bearer())
                        .param("accountId", String.valueOf(account))
                        .param("skipLines", "1").param("hasHeader", "true")
                        .param("dateColumn", "0").param("dateFormat", "dd/MM/yyyy")
                        .param("labelColumns", "1").param("debitColumn", "2").param("creditColumn", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(3))
                .andExpect(jsonPath("$.rows[0].duplicate").value(true))
                .andExpect(jsonPath("$.rows[1].duplicate").value(false))
                .andExpect(jsonPath("$.rows[1].direction").value("DEBIT"))
                .andExpect(jsonPath("$.rows[1].amount").value(45.10))
                .andExpect(jsonPath("$.rows[1].categoryId").value((int) groceries))
                .andExpect(jsonPath("$.rows[1].paymentMethod").value("CARD"))
                .andExpect(jsonPath("$.rows[2].direction").value("CREDIT"))
                .andExpect(jsonPath("$.rows[2].amount").value(2500.0))
                .andExpect(jsonPath("$.warnings.length()").value(1))
                .andReturn();

        List<Map<String, Object>> rows = read(preview, "$.rows[?(@.duplicate == false)]");
        String confirm = "{\"accountId\":" + account + ",\"rows\":" + toJson(rows) + "}";
        mockMvc.perform(as(s, post("/imports/confirm")).content(confirm))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imported").value(2));
        // Un second envoi des mêmes lignes n'importe rien
        mockMvc.perform(as(s, post("/imports/confirm")).content(confirm))
                .andExpect(jsonPath("$.imported").value(0));

        mockMvc.perform(as(s, get("/accounts/" + account))).andExpect(jsonPath("$.balance").value(2442.60));
    }

    @Test
    void ofxImportReadsSgmlStatements() throws Exception {
        Session s = newUser();
        long account = account(s, "Courant", accountType(s, "Courant", false), eurId(s), "0");
        String ofx = """
                OFXHEADER:100
                DATA:OFXSGML
                VERSION:102
                CHARSET:1252

                <OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><CURDEF>EUR
                <BANKTRANLIST><DTSTART>20260101<DTEND>20260131
                <STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20260103<TRNAMT>-23,50<FITID>ABC1<NAME>PRLV EDF<MEMO>Facture janvier</STMTTRN>
                <STMTTRN><TRNTYPE>CREDIT<DTPOSTED>20260115120000.000[+1:CET]<TRNAMT>100.00<FITID>ABC2<NAME>VIR REMBOURSEMENT</STMTTRN>
                </BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>
                """;
        MockMultipartFile file = new MockMultipartFile("file", "releve.ofx", "application/x-ofx",
                ofx.getBytes(StandardCharsets.ISO_8859_1));

        mockMvc.perform(multipart("/imports/ofx/preview").file(file)
                        .header("Authorization", s.bearer())
                        .param("accountId", String.valueOf(account)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(2))
                .andExpect(jsonPath("$.rows[0].label").value("PRLV EDF"))
                .andExpect(jsonPath("$.rows[0].notes").value("Facture janvier"))
                .andExpect(jsonPath("$.rows[0].amount").value(23.5))
                .andExpect(jsonPath("$.rows[0].importId").value("ofx:ABC1"))
                .andExpect(jsonPath("$.rows[0].paymentMethod").value("DIRECT_DEBIT"))
                .andExpect(jsonPath("$.rows[1].operationDate").value("2026-01-15"));
    }

    @Test
    void statsSplitExpensesByCategoryHierarchy() throws Exception {
        Session s = newUser();
        long account = account(s, "Courant", accountType(s, "Courant", false), eurId(s), "0");
        transaction(s, account, "DEBIT", "100", "Courses", categoryId(s, "Courses"), "2026-04-02");
        transaction(s, account, "DEBIT", "50", "Resto", categoryId(s, "Restaurants"), "2026-04-03");
        transaction(s, account, "DEBIT", "40", "Essence", categoryId(s, "Carburant"), "2026-04-04");
        // Un remboursement dans une catégorie de dépense vient en déduction
        transaction(s, account, "CREDIT", "10", "Remboursement resto", categoryId(s, "Restaurants"), "2026-04-05");
        transaction(s, account, "DEBIT", "10", "Inconnu", null, "2026-04-06");
        transaction(s, account, "CREDIT", "2000", "Salaire", categoryId(s, "Salaire net"), "2026-04-28");

        String period = "from=2026-04-01&to=2026-04-30";
        mockMvc.perform(as(s, get("/stats/summary?" + period)))
                .andExpect(jsonPath("$.income").value(2000.0))
                .andExpect(jsonPath("$.expenses").value(190.0))
                .andExpect(jsonPath("$.net").value(1810.0))
                .andExpect(jsonPath("$.savingsRate").value(0.905));

        mockMvc.perform(as(s, get("/stats/categories?" + period)))
                .andExpect(jsonPath("$.items[0].name").value("Alimentation"))
                .andExpect(jsonPath("$.items[0].amount").value(140.0))
                .andExpect(jsonPath("$.items[0].hasChildren").value(true))
                .andExpect(jsonPath("$.items[1].name").value("Transport"))
                .andExpect(jsonPath("$.items[2].name").value("Non catégorisé"));

        mockMvc.perform(as(s, get("/stats/categories?" + period + "&parentId=" + categoryId(s, "Alimentation"))))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].name").value("Courses"))
                .andExpect(jsonPath("$.items[1].amount").value(40.0));

        mockMvc.perform(as(s, get("/stats/monthly?from=2026-03-01&to=2026-04-30")))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[1].month").value("2026-04"))
                .andExpect(jsonPath("$.items[1].expenses").value(190.0));

        mockMvc.perform(as(s, get("/stats/payees?" + period)))
                .andExpect(jsonPath("$.items[0].payee").value("Courses"));

        mockMvc.perform(as(s, get("/stats/balance-history?from=2026-04-01&to=2026-04-30&granularity=WEEK")))
                .andExpect(jsonPath("$.points[-1:].value", contains(1810.0)));

        mockMvc.perform(as(s, get("/stats/category-trend?categoryId=" + categoryId(s, "Alimentation") + "&" + period)))
                .andExpect(jsonPath("$.items[0].amount").value(140.0));

        MvcResult savings = mockMvc.perform(as(s, get("/stats/savings?" + period))).andExpect(status().isOk()).andReturn();
        assertThat((Object) read(savings, "$.savingsRate")).isNotNull();
    }

    @Test
    void deletingACategoryMovesTransactionsToReplacement() throws Exception {
        Session s = newUser();
        long account = account(s, "Courant", accountType(s, "Courant", false), eurId(s), "0");
        long cafe = categoryId(s, "Cafés & snacks");
        long restaurants = categoryId(s, "Restaurants");
        long tx = transaction(s, account, "DEBIT", "3", "Café", cafe, "2026-01-01");

        mockMvc.perform(as(s, delete("/categories/" + cafe + "?replacementId=" + restaurants))).andExpect(status().isNoContent());
        mockMvc.perform(as(s, get("/transactions/" + tx))).andExpect(jsonPath("$.category.id").value((int) restaurants));

        // Une catégorie ne peut pas devenir enfant de sa propre sous-catégorie
        long food = categoryId(s, "Alimentation");
        mockMvc.perform(as(s, put("/categories/" + food))
                        .content("{\"name\":\"Alimentation\",\"type\":\"EXPENSE\",\"parentId\":" + restaurants + "}"))
                .andExpect(status().isBadRequest());
    }

    private static String toJson(List<Map<String, Object>> rows) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append('{');
            int j = 0;
            for (Map.Entry<String, Object> entry : rows.get(i).entrySet()) {
                if (j++ > 0) {
                    json.append(',');
                }
                Object value = entry.getValue();
                json.append('"').append(entry.getKey()).append("\":");
                json.append(value == null ? "null" : value instanceof String str ? "\"" + str + "\"" : value.toString());
            }
            json.append('}');
        }
        return json.append(']').toString();
    }
}
