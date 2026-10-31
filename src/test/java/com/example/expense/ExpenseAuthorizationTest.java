package com.example.expense;

import com.example.expense.expense.Expense;
import com.example.expense.expense.ExpenseRepository;
import com.example.expense.user.Role;
import com.example.expense.user.User;
import com.example.expense.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.seed=false")
@AutoConfigureMockMvc
@Transactional
class ExpenseAuthorizationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository users;

    @Autowired
    ExpenseRepository expenses;

    @Autowired
    JwtEncoder jwtEncoder;

    TestTokens tokens;
    User alice;
    User erin;
    Expense aliceDraft;
    String aliceToken;
    String bobToken;
    String erinToken;

    @BeforeEach
    void setUp() {
        tokens = new TestTokens(jwtEncoder);
        alice = users.save(new User("alice", "x", "Alice", Role.EMPLOYEE));
        User bob = users.save(new User("bob", "x", "Bob", Role.MANAGER));
        erin = users.save(new User("erin", "x", "Erin", Role.EMPLOYEE));

        aliceDraft = expenses.save(new Expense(alice, new BigDecimal("10.00"), "EUR", "Taxi"));
        expenses.save(new Expense(erin, new BigDecimal("20.00"), "EUR", "Lunch"));

        aliceToken = tokens.forUser(alice);
        bobToken = tokens.forUser(bob);
        erinToken = tokens.forUser(erin);
    }

    private static String body(String amount, String currency, String description) {
        return """
                {"amount":%s,"currency":"%s","description":"%s"}
                """.formatted(amount, currency, description);
    }

    @Test
    void createReturns201WithLocationAndOwner() throws Exception {
        mockMvc.perform(post("/api/expenses")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("15.00", "EUR", "Taxi")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/expenses/")))
                .andExpect(jsonPath("$.owner").value("alice"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void employeeListsOnlyOwnExpenses() throws Exception {
        mockMvc.perform(get("/api/expenses/mine")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].owner").value("alice"));
    }

    @Test
    void employeeCannotListAllExpenses() throws Exception {
        mockMvc.perform(get("/api/expenses")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerCanListAllExpenses() throws Exception {
        mockMvc.perform(get("/api/expenses")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void ownerCanReadOwnExpense() throws Exception {
        mockMvc.perform(get("/api/expenses/" + aliceDraft.getId())
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Taxi"));
    }

    @Test
    void strangerCannotReadExpense() throws Exception {
        mockMvc.perform(get("/api/expenses/" + aliceDraft.getId())
                        .header("Authorization", "Bearer " + erinToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerCanReadAnyExpense() throws Exception {
        mockMvc.perform(get("/api/expenses/" + aliceDraft.getId())
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk());
    }

    @Test
    void ownerCanUpdateDraft() throws Exception {
        mockMvc.perform(put("/api/expenses/" + aliceDraft.getId())
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("99.00", "EUR", "Updated description")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Updated description"));
    }

    @Test
    void strangerCannotUpdateExpense() throws Exception {
        mockMvc.perform(put("/api/expenses/" + aliceDraft.getId())
                        .header("Authorization", "Bearer " + erinToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("99.00", "EUR", "Hijacked")))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCanDeleteDraft() throws Exception {
        mockMvc.perform(delete("/api/expenses/" + aliceDraft.getId())
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void strangerCannotDeleteExpense() throws Exception {
        mockMvc.perform(delete("/api/expenses/" + aliceDraft.getId())
                        .header("Authorization", "Bearer " + erinToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeReadingMissingExpenseReturns403() throws Exception {
        mockMvc.perform(get("/api/expenses/999999")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void zeroAmountReturns400() throws Exception {
        mockMvc.perform(post("/api/expenses")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("0", "EUR", "Zero")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void negativeAmountReturns400() throws Exception {
        mockMvc.perform(post("/api/expenses")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("-5.00", "EUR", "Negative")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void moreThanTwoDecimalsReturns400() throws Exception {
        mockMvc.perform(post("/api/expenses")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("10.555", "EUR", "Too precise")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankDescriptionReturns400() throws Exception {
        mockMvc.perform(post("/api/expenses")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("10.00", "EUR", "   ")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lowercaseCurrencyReturns400() throws Exception {
        mockMvc.perform(post("/api/expenses")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("10.00", "eur", "Lowercase")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tokenWithoutRolesIsForbidden() throws Exception {
        mockMvc.perform(get("/api/expenses")
                        .header("Authorization", "Bearer " + tokens.withoutRoles(alice)))
                .andExpect(status().isForbidden());
    }
}
