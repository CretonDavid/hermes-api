package com.untitled.hermesapi;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiTest extends ApiTestSupport {

    @Test
    void registerReturnsA30DigitsAccountNumberAndSeedsDefaults() throws Exception {
        Session session = newUser();
        assertThat(session.accountNumber()).matches("\\d{30}");

        mockMvc.perform(as(session, get("/users/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceCurrency.code").value("EUR"));

        mockMvc.perform(as(session, get("/categories")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThan(50)))
                .andExpect(jsonPath("$[?(@.name == 'Alimentation')].parentId", contains(nullValue())));

        mockMvc.perform(as(session, get("/currencies")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'USD')].custom", contains(false)));
    }

    @Test
    void loginRequiresTheFullAccountNumber() throws Exception {
        Session session = newUser();
        // Mêmes 6 premiers chiffres, fin différente : doit être refusé
        String forged = session.accountNumber().substring(0, 6) + "0".repeat(24);
        if (forged.equals(session.accountNumber())) {
            forged = session.accountNumber().substring(0, 6) + "1".repeat(24);
        }

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountNumber\":\"" + forged + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountNumber\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.accountNumber").exists());
    }

    @Test
    void protectedEndpointsReturn401WithoutToken() throws Exception {
        mockMvc.perform(get("/accounts")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/accounts").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenGivesANewAccessToken() throws Exception {
        Session session = newUser();

        mockMvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refresh_token\":\"" + session.refreshToken() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isString());

        // Un access token ne peut pas servir de refresh token
        mockMvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refresh_token\":\"" + session.accessToken() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disabledUserCannotLoginUntilReactivated() throws Exception {
        Session session = newUser();
        mockMvc.perform(as(session, post("/users/me/disable"))).andExpect(status().isNoContent());

        mockMvc.perform(as(session, get("/accounts"))).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountNumber\":\"" + session.accountNumber() + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));

        mockMvc.perform(post("/auth/reactivate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountNumber\":\"" + session.accountNumber() + "\"}"))
                .andExpect(status().isOk());
        Session again = login(session.accountNumber());
        mockMvc.perform(as(again, get("/accounts"))).andExpect(status().isOk());
    }

    @Test
    void deleteRemovesTheUserAndAllData() throws Exception {
        Session session = newUser();
        long typeId = readId(mockMvc.perform(as(session, post("/account-types"))
                .content("{\"name\":\"Courant\",\"savings\":false}")).andReturn());
        long savingsTypeId = readId(mockMvc.perform(as(session, post("/account-types"))
                .content("{\"name\":\"Livret\",\"savings\":true}")).andReturn());
        long a = readId(mockMvc.perform(as(session, post("/accounts"))
                .content("{\"name\":\"A\",\"accountTypeId\":" + typeId + ",\"currencyId\":" + eurId(session) + "}")).andReturn());
        long b = readId(mockMvc.perform(as(session, post("/accounts"))
                .content("{\"name\":\"B\",\"accountTypeId\":" + savingsTypeId + ",\"currencyId\":" + eurId(session) + "}")).andReturn());
        mockMvc.perform(as(session, post("/transfers"))
                        .content("{\"fromAccountId\":" + a + ",\"toAccountId\":" + b + ",\"amount\":10,\"operationDate\":\"2026-01-10\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(as(session, post("/currencies")).content("{\"code\":\"BTC\",\"symbol\":\"₿\",\"name\":\"Bitcoin\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(as(session, delete("/users/me")).content("{\"accountNumber\":\"" + "9".repeat(30) + "\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(as(session, delete("/users/me")).content("{\"accountNumber\":\"" + session.accountNumber() + "\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountNumber\":\"" + session.accountNumber() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

}
