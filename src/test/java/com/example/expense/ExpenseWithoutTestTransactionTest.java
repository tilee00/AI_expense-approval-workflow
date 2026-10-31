package com.example.expense;

import com.example.expense.expense.Expense;
import com.example.expense.expense.ExpenseRepository;
import com.example.expense.user.Role;
import com.example.expense.user.User;
import com.example.expense.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deliberately NOT @Transactional: this reproduces how the running application
 * behaves, where no test transaction keeps a Hibernate session open. Without
 * this class, lazy-loading bugs hide behind the test transaction.
 */
@SpringBootTest(properties = {
        "app.seed=false",
        "spring.datasource.url=jdbc:h2:mem:expenses-nontx;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class ExpenseWithoutTestTransactionTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository users;

    @Autowired
    ExpenseRepository expenses;

    @Autowired
    JwtEncoder jwtEncoder;

    @Test
    void ownerCanSubmitWithoutAnOpenTestTransaction() throws Exception {
        User alice = users.save(new User("tx-alice", "x", "Alice", Role.EMPLOYEE));
        Expense draft = expenses.save(new Expense(alice, new BigDecimal("10.00"), "EUR", "Taxi"));

        mockMvc.perform(post("/api/expenses/" + draft.getId() + "/submit")
                        .header("Authorization", "Bearer " + new TestTokens(jwtEncoder).forUser(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.owner").value("tx-alice"));
    }
}
