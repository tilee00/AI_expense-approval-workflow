package com.example.expense;

import com.example.expense.expense.Expense;
import com.example.expense.user.Role;
import com.example.expense.user.User;
import com.example.expense.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The optimistic-lock failure is thrown while the transaction commits, after
 * the service method has returned, so it never passes through the service's
 * own error handling. This proves the web layer maps it to 409 anyway.
 */
@SpringBootTest(properties = "app.seed=false")
@AutoConfigureMockMvc
@Import(OptimisticLockConflictTest.StaleWriteController.class)
@Transactional
class OptimisticLockConflictTest {

    @RestController
    static class StaleWriteController {

        @GetMapping("/test/stale-write")
        void staleWrite() {
            throw new ObjectOptimisticLockingFailureException(Expense.class, 1L);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository users;

    @Autowired
    JwtEncoder jwtEncoder;

    String token;

    @BeforeEach
    void setUp() {
        User alice = users.save(new User("lock-alice", "x", "Alice", Role.EMPLOYEE));
        token = new TestTokens(jwtEncoder).forUser(alice);
    }

    @Test
    void staleWriteReturns409WithAUsefulMessage() throws Exception {
        mockMvc.perform(get("/test/stale-write")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message", containsString("modified")));
    }
}
