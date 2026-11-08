package com.hermes.api;

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
    void transactionsCanBeSortedTotalledAndCategorizedInBulk() throws Exception {
        Session s = newUser();
        long type = accountType(s, "Courant", false);
        long current = account(s, "Courant", type, eurId(s), "0");
        long savings = account(s, "Livret", type, eurId(s), "0");
        long small = transaction(s, current, "DEBIT", "10", "Zèbre", null, "2026-01-02");
        long big = transaction(s, current, "CREDIT", "200", "abricot", null, "2026-01-01");
        mockMvc.perform(as(s, post("/transfers")).content("{\"fromAccountId\":" + current + ",\"toAccountId\":" + savings
                + ",\"amount\":50,\"operationDate\":\"2026-01-03\"}")).andExpect(status().isCreated());

        mockMvc.perform(as(s, get("/transactions?accountIds=" + current + "&sort=amount&direction=desc")))
                .andExpect(jsonPath("$.content[0].id").value(big));
        mockMvc.perform(as(s, get("/transactions?accountIds=" + current + "&sort=label&direction=asc")))
                .andExpect(jsonPath("$.content[0].id").value(big));
        mockMvc.perform(as(s, get("/transactions?sort=account&direction=desc")))
                .andExpect(jsonPath("$.content[0].accountName").value("Livret"));

        mockMvc.perform(as(s, get("/transactions/totals?accountIds=" + current)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currencyCode").value("EUR"))
                .andExpect(jsonPath("$[0].count").value(3))
                .andExpect(jsonPath("$[0].credits").value(200.0))
                .andExpect(jsonPath("$[0].debits").value(60.0))
                .andExpect(jsonPath("$[0].net").value(140.0));

        Number transferId = read(mockMvc.perform(as(s, get("/transactions?kind=TRANSFER&accountIds=" + current))).andReturn(), "$.content[0].id");
        long category = categoryId(s, "Courses");
        mockMvc.perform(as(s, patch("/transactions/category"))
                        .content("{\"transactionIds\":[" + small + "," + transferId + "],\"categoryId\":" + category + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(1))
                .andExpect(jsonPath("$.skippedTransfers").value(1));
        mockMvc.perform(as(s, get("/transactions/" + small))).andExpect(jsonPath("$.category.id").value(category));

        Session other = newUser();
        mockMvc.perform(as(other, patch("/transactions/category")).content("{\"transactionIds\":[" + small + "]}"))
                .andExpect(status().isNotFound());
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
    void accountNameIsLimitedTo50Characters() throws Exception {
        Session s = newUser();
        long type = accountType(s, "Courant", false);
        String body = ",\"accountTypeId\":" + type + ",\"currencyId\":" + eurId(s) + "}";

        mockMvc.perform(as(s, post("/accounts")).content("{\"name\":\"" + "x".repeat(50) + "\"" + body))
                .andExpect(status().isCreated());
        mockMvc.perform(as(s, post("/accounts")).content("{\"name\":\"" + "x".repeat(51) + "\"" + body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
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
    void recurringTransferBetweenCurrenciesRequiresAnAmountOrARate() throws Exception {
        Session s = newUser();
        long type = accountType(s, "Courant", false);
        long eur = account(s, "Euros", type, eurId(s), "0");
        long usd = account(s, "Dollars", type, currencyId(s, "USD"), "0");
        String today = LocalDate.now().toString();
        String base = "{\"accountId\":" + eur + ",\"targetAccountId\":" + usd + ",\"amount\":100,\"label\":\"Change\","
                + "\"frequency\":\"MONTHLY\",\"interval\":1,\"startDate\":\"" + today + "\",\"active\":true";

        // Sans taux ni montant reçu : refus explicite, rien n'est créé
        mockMvc.perform(as(s, post("/recurring-transactions")).content(base + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Aucun taux EUR/USD")));
        mockMvc.perform(as(s, get("/recurring-transactions"))).andExpect(jsonPath("$.length()").value(0));

        // Avec le montant reçu : l'échéance du jour est générée
        mockMvc.perform(as(s, post("/recurring-transactions")).content(base + ",\"targetAmount\":110}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.occurrenceCount").value(1));

        // Avec un taux : l'échéance du jour est générée aussi
        mockMvc.perform(as(s, post("/exchange-rates")).content("{\"baseCurrencyId\":" + eurId(s) + ",\"quoteCurrencyId\":"
                        + currencyId(s, "USD") + ",\"rate\":1.1,\"date\":\"" + today + "\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(as(s, post("/recurring-transactions")).content(base + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.occurrenceCount").value(1));
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
    void reimbursedRecurringExpenseIsTrackedPerPayerAndYear() throws Exception {
        Session s = newUser();
        long account = account(s, "Courant", accountType(s, "Courant", false), eurId(s), "0");
        long streaming = categoryId(s, "Streaming");
        String recurringBody = "{\"accountId\":" + account + ",\"direction\":\"DEBIT\",\"amount\":16.99,\"label\":\"Spotify Duo\","
                + "\"categoryId\":" + streaming + ",\"frequency\":\"MONTHLY\",\"interval\":1,"
                + "\"startDate\":\"2024-01-15\",\"endDate\":\"2025-12-31\",\"active\":true,\"reimbursementPayers\":";

        // Les parts des tiers ne peuvent pas dépasser 100 %
        mockMvc.perform(as(s, post("/recurring-transactions")).content(recurringBody
                        + "[{\"name\":\"Alex\",\"share\":60},{\"name\":\"Sam\",\"share\":50}]}"))
                .andExpect(status().isBadRequest());

        // Abonnement de 2024 à 2025, remboursé pour moitié par Alex une fois par an, et à 25 % par Sam chaque trimestre
        MvcResult created = mockMvc.perform(as(s, post("/recurring-transactions")).content(recurringBody
                        + "[{\"name\":\"Alex\",\"share\":50,\"startDate\":\"2024-03-10\"},"
                        + "{\"name\":\"Sam\",\"share\":25,\"period\":\"QUARTERLY\",\"startDate\":\"2024-01-01\"}]}"))
                .andExpect(status().isCreated())
                // La date de début est ramenée au début de la période
                .andExpect(jsonPath("$.reimbursementPayers[0].startDate").value("2024-01-01"))
                .andExpect(jsonPath("$.reimbursementPayers.length()").value(2))
                .andExpect(jsonPath("$.reimbursementPayers[0].name").value("Alex"))
                .andExpect(jsonPath("$.reimbursementPayers[0].period").value("YEARLY"))
                .andExpect(jsonPath("$.reimbursementPayers[1].period").value("QUARTERLY"))
                .andReturn();
        long recurring = readId(created);
        long alex = ((Number) read(created, "$.reimbursementPayers[0].id")).longValue();
        long sam = ((Number) read(created, "$.reimbursementPayers[1].id")).longValue();
        String base = "/recurring-transactions/" + recurring + "/reimbursements";

        long refund2024 = transaction(s, account, "CREDIT", "101.94", "Virement Alex", null, "2025-01-20");
        long partial2025 = transaction(s, account, "CREDIT", "50", "Virement Alex", null, "2025-06-01");
        long samQ1 = transaction(s, account, "CREDIT", "12.74", "Virement Sam", null, "2025-04-02");
        long expense = transaction(s, account, "DEBIT", "10", "Autre", null, "2025-06-01");

        mockMvc.perform(as(s, post(base)).content("{\"payerId\":" + alex + ",\"transactionId\":" + refund2024 + ",\"period\":\"2024-06-30\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(s, post(base)).content("{\"payerId\":" + alex + ",\"transactionId\":" + partial2025 + ",\"period\":\"2025-01-01\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(s, post(base)).content("{\"payerId\":" + sam + ",\"transactionId\":" + samQ1 + ",\"period\":\"2025-02-10\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(s, post(base)).content("{\"payerId\":" + alex + ",\"transactionId\":" + expense + ",\"period\":\"2025-01-01\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(as(s, get("/recurring-transactions/reimbursements")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].payers.length()").value(2))
                // Alex : la moitié de 12 × 16,99 = 101,94 par an
                .andExpect(jsonPath("$[0].payers[0].periods.length()").value(2))
                .andExpect(jsonPath("$[0].payers[0].periods[0].start").value("2025-01-01"))
                .andExpect(jsonPath("$[0].payers[0].periods[0].due").value(101.94))
                .andExpect(jsonPath("$[0].payers[0].periods[0].remaining").value(51.94))
                .andExpect(jsonPath("$[0].payers[0].periods[0].status").value("PARTIAL"))
                .andExpect(jsonPath("$[0].payers[0].periods[1].start").value("2024-01-01"))
                .andExpect(jsonPath("$[0].payers[0].periods[1].status").value("PAID"))
                .andExpect(jsonPath("$[0].payers[0].periods[1].payments[0].transactionId").value((int) refund2024))
                // Sam : le quart de 3 × 16,99 = 12,74 par trimestre, sur 8 trimestres
                .andExpect(jsonPath("$[0].payers[1].periods.length()").value(8))
                .andExpect(jsonPath("$[0].payers[1].periods[3].start").value("2025-01-01"))
                .andExpect(jsonPath("$[0].payers[1].periods[3].due").value(12.74))
                .andExpect(jsonPath("$[0].payers[1].periods[3].status").value("PAID"))
                .andExpect(jsonPath("$[0].payers[1].periods[2].status").value("UNPAID"));

        // Le remboursement prend la catégorie de la dépense et vient en déduction dans les statistiques
        mockMvc.perform(as(s, get("/transactions/" + refund2024)))
                .andExpect(jsonPath("$.category.id").value((int) streaming))
                .andExpect(jsonPath("$.reimbursement.recurringTransactionId").value((int) recurring))
                .andExpect(jsonPath("$.reimbursement.payerName").value("Alex"))
                .andExpect(jsonPath("$.reimbursement.periodStart").value("2024-01-01"));
        mockMvc.perform(as(s, get("/stats/summary?from=2024-01-01&to=2025-01-31&accountIds=" + account)))
                // 13 prélèvements de 16,99 (220,87) moins le remboursement de 101,94
                .andExpect(jsonPath("$.expenses").value(118.93));

        mockMvc.perform(as(s, delete(base + "/" + partial2025))).andExpect(status().isNoContent());
        mockMvc.perform(as(s, get("/recurring-transactions/reimbursements")))
                .andExpect(jsonPath("$[0].payers[0].periods[0].status").value("UNPAID"));

        // Retirer Sam détache ses remboursements, Alex garde les siens
        mockMvc.perform(as(s, put("/recurring-transactions/" + recurring)).content(recurringBody
                        + "[{\"id\":" + alex + ",\"name\":\"Alex\",\"share\":50}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reimbursementPayers.length()").value(1));
        mockMvc.perform(as(s, get("/transactions/" + samQ1))).andExpect(jsonPath("$.reimbursement").doesNotExist());
        mockMvc.perform(as(s, get("/transactions/" + refund2024))).andExpect(jsonPath("$.reimbursement.payerId").value((int) alex));

        // Supprimer la récurrence conserve les opérations
        mockMvc.perform(as(s, delete("/recurring-transactions/" + recurring))).andExpect(status().isNoContent());
        mockMvc.perform(as(s, get("/recurring-transactions/reimbursements"))).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(as(s, get("/transactions/" + refund2024)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reimbursement").doesNotExist());
    }

    @Test
    void reimbursementStartsAtChosenPeriodAndCarriesSurplusForward() throws Exception {
        Session s = newUser();
        long account = account(s, "Courant", accountType(s, "Courant", false), eurId(s), "0");
        // 10 € par mois depuis 2024, Alex en rembourse la moitié chaque année à partir de 2025
        MvcResult created = mockMvc.perform(as(s, post("/recurring-transactions")).content(
                        "{\"accountId\":" + account + ",\"direction\":\"DEBIT\",\"amount\":10,\"label\":\"Crunchyroll\","
                                + "\"frequency\":\"MONTHLY\",\"interval\":1,\"startDate\":\"2024-01-05\","
                                + "\"active\":true,\"reimbursementPayers\":[{\"name\":\"Alex\",\"share\":50,\"startDate\":\"2025-06-01\"},"
                                + "{\"name\":\"Sam\",\"share\":10}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reimbursementPayers[0].startDate").value("2025-01-01"))
                // Sans date, un nouveau tiers doit à partir de la période en cours
                .andExpect(jsonPath("$.reimbursementPayers[1].startDate").value(LocalDate.now().withDayOfYear(1).toString()))
                .andReturn();
        long recurring = readId(created);
        long alex = ((Number) read(created, "$.reimbursementPayers[0].id")).longValue();
        // 200 € versés pour 2025 alors que 60 € sont dus : 140 € d'avance reportés sur 2026
        long refund = transaction(s, account, "CREDIT", "200", "Virement Alex", null, "2025-12-20");
        mockMvc.perform(as(s, post("/recurring-transactions/" + recurring + "/reimbursements"))
                        .content("{\"payerId\":" + alex + ",\"transactionId\":" + refund + ",\"period\":\"2025-01-01\"}"))
                .andExpect(status().isNoContent());

        int year = LocalDate.now().getYear();
        mockMvc.perform(as(s, get("/recurring-transactions/reimbursements")))
                // Rien n'est dû pour 2024, antérieure au début
                .andExpect(jsonPath("$[0].payers[0].periods.length()").value(year - 2025 + 1))
                .andExpect(jsonPath("$[0].payers[0].periods[-1].start").value("2025-01-01"))
                .andExpect(jsonPath("$[0].payers[0].periods[-1].due").value(60.0))
                .andExpect(jsonPath("$[0].payers[0].periods[-1].status").value("PAID"))
                .andExpect(jsonPath("$[0].payers[0].periods[-1].surplus").value(140.0))
                .andExpect(jsonPath("$[0].payers[0].periods[-2].start").value("2026-01-01"))
                .andExpect(jsonPath("$[0].payers[0].periods[-2].remaining").value(0))
                .andExpect(jsonPath("$[0].payers[0].periods[-2].status").value("PAID"));

        // Les opérations générées par la récurrence se filtrent par récurrence
        mockMvc.perform(as(s, get("/transactions?recurringTransactionId=" + recurring + "&size=100")))
                .andExpect(jsonPath("$.content[0].recurringTransactionId").value((int) recurring))
                .andExpect(jsonPath("$.content[?(@.label == 'Virement Alex')]").isEmpty());
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
