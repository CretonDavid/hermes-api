package com.untitled.hermesapi;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class ApiTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    protected record Session(String accountNumber, String accessToken, String refreshToken) {
        String bearer() {
            return "Bearer " + accessToken;
        }
    }

    protected Session newUser() throws Exception {
        MvcResult register = mockMvc.perform(post("/auth/register")).andReturn();
        String accountNumber = JsonPath.read(register.getResponse().getContentAsString(), "$.accountNumber");
        return login(accountNumber);
    }

    protected Session login(String accountNumber) throws Exception {
        MvcResult login = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountNumber\":\"" + accountNumber + "\"}"))
                .andReturn();
        String body = login.getResponse().getContentAsString();
        return new Session(accountNumber, JsonPath.read(body, "$.access_token"), JsonPath.read(body, "$.refresh_token"));
    }

    protected MockHttpServletRequestBuilder as(Session session, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", session.bearer()).contentType(MediaType.APPLICATION_JSON);
    }

    protected String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    protected <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(body(result), path);
    }

    protected long readId(MvcResult result) throws Exception {
        Number id = JsonPath.read(body(result), "$.id");
        return id.longValue();
    }

    protected long currencyId(Session session, String code) throws Exception {
        MvcResult result = mockMvc.perform(as(session, get("/currencies"))).andReturn();
        List<Number> ids = JsonPath.read(body(result), "$[?(@.code == '" + code + "')].id");
        return ids.getFirst().longValue();
    }

    protected long eurId(Session session) throws Exception {
        return currencyId(session, "EUR");
    }

    protected long categoryId(Session session, String name) throws Exception {
        MvcResult result = mockMvc.perform(as(session, get("/categories"))).andReturn();
        List<Number> ids = JsonPath.read(body(result), "$[?(@.name == '" + name + "')].id");
        return ids.getFirst().longValue();
    }

    protected long accountType(Session session, String name, boolean savings) throws Exception {
        return readId(mockMvc.perform(as(session, post("/account-types"))
                .content("{\"name\":\"" + name + "\",\"savings\":" + savings + "}")).andReturn());
    }

    protected long account(Session session, String name, long typeId, long currencyId, String initialBalance) throws Exception {
        return readId(mockMvc.perform(as(session, post("/accounts"))
                .content("{\"name\":\"" + name + "\",\"accountTypeId\":" + typeId + ",\"currencyId\":" + currencyId
                        + ",\"initialBalance\":" + initialBalance + ",\"openingDate\":\"2025-01-01\"}")).andReturn());
    }

    protected long transaction(Session session, long accountId, String direction, String amount, String label,
                               Long categoryId, String date) throws Exception {
        return readId(mockMvc.perform(as(session, post("/transactions"))
                .content("{\"accountId\":" + accountId + ",\"direction\":\"" + direction + "\",\"amount\":" + amount
                        + ",\"label\":\"" + label + "\",\"categoryId\":" + categoryId + ",\"operationDate\":\"" + date + "\"}"))
                .andReturn());
    }
}
