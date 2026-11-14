package com.hermes.api;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Budgets spéciaux : affectation des crédits, réduction par les retraits, verrouillage et utilisation.
 */
class GoalApiTest extends ApiTestSupport {

    private static final String TODAY = LocalDate.now().toString();

    private long goal(Session s, String name, long account, String target, String initial) throws Exception {
        return readId(mockMvc.perform(as(s, post("/goals")).content("{\"name\":\"" + name + "\",\"accountId\":" + account
                        + ",\"targetAmount\":" + target + ",\"initialAmount\":" + initial + "}"))
                .andExpect(status().isCreated()).andReturn());
    }

    private String transfer(long from, long to, String amount, String allocations) {
        return "{\"fromAccountId\":" + from + ",\"toAccountId\":" + to + ",\"amount\":" + amount
                + ",\"operationDate\":\"" + TODAY + "\",\"goalAllocations\":[" + allocations + "]}";
    }

    private static String allocation(long goal, String amount) {
        return "{\"goalId\":" + goal + ",\"amount\":" + amount + "}";
    }

    @Test
    void transfersFillTheGoalAndWithdrawalsOnlyTakeWhatTheBalanceCannotCover() throws Exception {
        Session s = newUser();
        long checking = account(s, "Courant", accountType(s, "Courant", false), eurId(s), "5000");
        long livret = account(s, "Livret A", accountType(s, "Livret", true), eurId(s), "1600");
        long moto = goal(s, "Moto", livret, "1500", "500");

        // Sur 400 €, 400 pour la moto : le livret a 2 000 € dont 900 pour la moto
        mockMvc.perform(as(s, post("/transfers")).content(transfer(checking, livret, "400", allocation(moto, "400"))))
                .andExpect(status().isCreated());
        mockMvc.perform(as(s, get("/goals")))
                .andExpect(jsonPath("$[0].savedAmount").value(900.0))
                .andExpect(jsonPath("$[0].remaining").value(600.0));

        // Retrait de 1 000 € : il reste 1 000 €, la moto n'est pas touchée
        mockMvc.perform(as(s, post("/transfers")).content(transfer(livret, checking, "1000", "")))
                .andExpect(status().isCreated());
        mockMvc.perform(as(s, get("/goals"))).andExpect(jsonPath("$[0].savedAmount").value(900.0));

        // Retrait de 800 € : il ne reste que 200 €, la moto descend à 200
        mockMvc.perform(as(s, post("/transfers")).content(transfer(livret, checking, "800", "")))
                .andExpect(status().isCreated());
        mockMvc.perform(as(s, get("/goals")))
                .andExpect(jsonPath("$[0].savedAmount").value(200.0))
                .andExpect(jsonPath("$[0].withdrawn").value(700.0));
    }

    @Test
    void withdrawalsReduceGoalsOfTheAccountProRata() throws Exception {
        Session s = newUser();
        long checking = account(s, "Courant", accountType(s, "Courant", false), eurId(s), "0");
        long livret = account(s, "Livret A", accountType(s, "Livret", true), eurId(s), "1200");
        goal(s, "Moto", livret, "2000", "900");
        goal(s, "Vacances", livret, "2000", "300");

        // Il manque 400 € : la moto perd 300, les vacances 100
        mockMvc.perform(as(s, post("/transfers")).content(transfer(livret, checking, "400", "")))
                .andExpect(status().isCreated());
        mockMvc.perform(as(s, get("/goals")))
                .andExpect(jsonPath("$[0].savedAmount").value(600.0))
                .andExpect(jsonPath("$[1].savedAmount").value(200.0));
    }

    @Test
    void aReachedGoalIsLockedAndUsingItReleasesItsAmount() throws Exception {
        Session s = newUser();
        long checking = account(s, "Courant", accountType(s, "Courant", false), eurId(s), "5000");
        long livret = account(s, "Livret A", accountType(s, "Livret", true), eurId(s), "1000");
        long moto = goal(s, "Moto", livret, "1000", "800");
        long vacances = goal(s, "Vacances", livret, "500", "0");

        // Plus que ce qu'il manque, ou plus que le crédit : refusé
        mockMvc.perform(as(s, post("/transfers")).content(transfer(checking, livret, "500", allocation(moto, "300"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(s, post("/transfers")).content(transfer(checking, livret, "100",
                        allocation(moto, "100") + "," + allocation(vacances, "50"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(as(s, post("/transfers")).content(transfer(checking, livret, "500",
                        allocation(moto, "200") + "," + allocation(vacances, "100"))))
                .andExpect(status().isCreated());
        mockMvc.perform(as(s, get("/goals")))
                .andExpect(jsonPath("$[0].reached").value(true))
                .andExpect(jsonPath("$[1].savedAmount").value(100.0));

        // Objectif atteint : verrouillé
        mockMvc.perform(as(s, post("/transfers")).content(transfer(checking, livret, "50", allocation(moto, "10"))))
                .andExpect(status().isBadRequest());

        // La moto est achetée : son montant est libéré avant le retrait du jour, les vacances ne sont pas touchées
        mockMvc.perform(as(s, post("/goals/" + moto + "/use")).content("{\"date\":\"" + TODAY + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usedDate").value(TODAY))
                .andExpect(jsonPath("$.savedAmount").value(1000.0));
        mockMvc.perform(as(s, post("/transfers")).content(transfer(livret, checking, "1350", "")))
                .andExpect(status().isCreated());
        mockMvc.perform(as(s, get("/goals")))
                .andExpect(jsonPath("$[1].savedAmount").value(100.0));
    }

    @Test
    void freeMoneyOfTheAccountCanBeMovedIntoAndOutOfAGoal() throws Exception {
        Session s = newUser();
        long livret = account(s, "Livret A", accountType(s, "Livret", true), eurId(s), "1000");
        long moto = goal(s, "Moto", livret, "1500", "200");
        goal(s, "Parachute", livret, "1000", "200");
        String adjust = "{\"amount\":%s}";

        // 600 € libres : on en affecte 500 à la moto
        mockMvc.perform(as(s, post("/goals/" + moto + "/adjust")).content(adjust.formatted("500")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedAmount").value(700.0));
        // Plus que l'argent libre (100 €) : refusé
        mockMvc.perform(as(s, post("/goals/" + moto + "/adjust")).content(adjust.formatted("200")))
                .andExpect(status().isBadRequest());

        // On rend 300 € libres, mais pas plus que ce que le budget a de côté
        mockMvc.perform(as(s, post("/goals/" + moto + "/adjust")).content(adjust.formatted("-300")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedAmount").value(400.0))
                .andExpect(jsonPath("$.withdrawn").value(0.0));
        mockMvc.perform(as(s, post("/goals/" + moto + "/adjust")).content(adjust.formatted("-500")))
                .andExpect(status().isBadRequest());

        // Pas plus que ce qu'il manque pour l'objectif
        mockMvc.perform(as(s, post("/goals/" + moto + "/adjust")).content(adjust.formatted("400")))
                .andExpect(status().isOk());
        mockMvc.perform(as(s, get("/goals")))
                .andExpect(jsonPath("$[0].savedAmount").value(800.0))
                .andExpect(jsonPath("$[1].savedAmount").value(200.0));
    }

    @Test
    void initialAmountCannotExceedWhatIsFreeOnTheAccount() throws Exception {
        Session s = newUser();
        long livret = account(s, "Livret A", accountType(s, "Livret", true), eurId(s), "1000");
        goal(s, "Moto", livret, "2000", "800");

        mockMvc.perform(as(s, post("/goals")).content("{\"name\":\"Vacances\",\"accountId\":" + livret
                        + ",\"targetAmount\":1000,\"initialAmount\":300}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void allocationsFollowTheCreditAndDisappearWithItsAccount() throws Exception {
        Session s = newUser();
        long livret = account(s, "Livret A", accountType(s, "Livret", true), eurId(s), "0");
        long moto = goal(s, "Moto", livret, "1500", "0");

        // Des intérêts peuvent aussi alimenter un budget spécial
        String credit = "{\"accountId\":" + livret + ",\"direction\":\"CREDIT\",\"amount\":50,\"label\":\"Intérêts\","
                + "\"operationDate\":\"" + TODAY + "\",\"goalAllocations\":[" + allocation(moto, "50") + "]}";
        long id = readId(mockMvc.perform(as(s, post("/transactions")).content(credit))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.goalAllocations[0].goalName").value("Moto"))
                .andReturn());
        mockMvc.perform(as(s, get("/goals"))).andExpect(jsonPath("$[0].savedAmount").value(50.0));

        // Une dépense ne peut pas alimenter un budget spécial
        mockMvc.perform(as(s, put("/transactions/" + id)).content(credit.replace("CREDIT", "DEBIT")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(as(s, delete("/transactions/" + id))).andExpect(status().isNoContent());
        mockMvc.perform(as(s, get("/goals"))).andExpect(jsonPath("$[0].savedAmount").value(0.0));

        mockMvc.perform(as(s, delete("/accounts/" + livret))).andExpect(status().isNoContent());
        mockMvc.perform(as(s, get("/goals"))).andExpect(jsonPath("$.length()").value(0));
    }
}
