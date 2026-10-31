package com.example.expense;

import com.example.expense.expense.Expense;
import com.example.expense.expense.ExpenseRepository;
import com.example.expense.expense.ExpenseStatus;
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
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.seed=false")
@AutoConfigureMockMvc
@Transactional
class ExpenseStateTest {

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
    Expense draft;
    Expense submitted;
    Expense approved;
    Expense rejected;
    String aliceToken;
    String bobToken;
    String carolToken;
    String daveToken;
    String erinToken;

    @BeforeEach
    void setUp() {
        tokens = new TestTokens(jwtEncoder);
        alice = users.save(new User("alice", "x", "Alice", Role.EMPLOYEE));
        User bob = users.save(new User("bob", "x", "Bob", Role.MANAGER));
        User carol = users.save(new User("carol", "x", "Carol", Role.FINANCE));
        User dave = users.save(new User("dave", "x", "Dave", Role.ADMIN));
        User erin = users.save(new User("erin", "x", "Erin", Role.EMPLOYEE));

        draft = expenses.save(new Expense(alice, new BigDecimal("10.00"), "EUR", "Draft"));
        submitted = expenses.save(expense(ExpenseStatus.SUBMITTED, "Submitted"));
        approved = expenses.save(expense(ExpenseStatus.APPROVED, "Approved"));
        rejected = expenses.save(expense(ExpenseStatus.REJECTED, "Rejected"));

        aliceToken = tokens.forUser(alice);
        bobToken = tokens.forUser(bob);
        carolToken = tokens.forUser(carol);
        daveToken = tokens.forUser(dave);
        erinToken = tokens.forUser(erin);
    }

    private Expense expense(ExpenseStatus status, String description) {
        Expense expense = new Expense(alice, new BigDecimal("10.00"), "EUR", description);
        expense.setStatus(status);
        if (status != ExpenseStatus.DRAFT) {
            expense.setSubmittedAt(Instant.now().minusSeconds(60));
        }
        return expense;
    }

    @Test
    void ownerCanSubmitDraft() throws Exception {
        mockMvc.perform(post("/api/expenses/" + draft.getId() + "/submit")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.submittedAt").isNotEmpty());
    }

    @Test
    void strangerCannotSubmit() throws Exception {
        mockMvc.perform(post("/api/expenses/" + draft.getId() + "/submit")
                        .header("Authorization", "Bearer " + erinToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerCanApproveSubmitted() throws Exception {
        mockMvc.perform(post("/api/expenses/" + submitted.getId() + "/approve")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedBy").value("bob"));
    }

    @Test
    void employeeCannotApprove() throws Exception {
        mockMvc.perform(post("/api/expenses/" + submitted.getId() + "/approve")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void financeCannotApprove() throws Exception {
        mockMvc.perform(post("/api/expenses/" + submitted.getId() + "/approve")
                        .header("Authorization", "Bearer " + carolToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanApprove() throws Exception {
        mockMvc.perform(post("/api/expenses/" + submitted.getId() + "/approve")
                        .header("Authorization", "Bearer " + daveToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void managerCanRejectSubmitted() throws Exception {
        mockMvc.perform(post("/api/expenses/" + submitted.getId() + "/reject")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void financeCanPayApproved() throws Exception {
        mockMvc.perform(post("/api/expenses/" + approved.getId() + "/pay")
                        .header("Authorization", "Bearer " + carolToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAt").isNotEmpty());
    }

    @Test
    void employeeCannotPay() throws Exception {
        mockMvc.perform(post("/api/expenses/" + approved.getId() + "/pay")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerApprovingMissingExpenseReturns404() throws Exception {
        mockMvc.perform(post("/api/expenses/999999/approve")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void editingSubmittedExpenseReturns409() throws Exception {
        mockMvc.perform(put("/api/expenses/" + submitted.getId())
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":11.00,"currency":"EUR","description":"Changed"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void submittingTwiceReturns409() throws Exception {
        mockMvc.perform(post("/api/expenses/" + submitted.getId() + "/submit")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isConflict());
    }

    @Test
    void approvingTwiceReturns409() throws Exception {
        mockMvc.perform(post("/api/expenses/" + approved.getId() + "/approve")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isConflict());
    }

    @Test
    void payingBeforeApprovalReturns409() throws Exception {
        mockMvc.perform(post("/api/expenses/" + submitted.getId() + "/pay")
                        .header("Authorization", "Bearer " + carolToken))
                .andExpect(status().isConflict());
    }

    @Test
    void payingRejectedExpenseReturns409() throws Exception {
        mockMvc.perform(post("/api/expenses/" + rejected.getId() + "/pay")
                        .header("Authorization", "Bearer " + carolToken))
                .andExpect(status().isConflict());
    }
}
