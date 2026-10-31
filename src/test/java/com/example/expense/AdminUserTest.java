package com.example.expense;

import com.example.expense.user.Role;
import com.example.expense.user.User;
import com.example.expense.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.seed=false")
@AutoConfigureMockMvc
@Transactional
class AdminUserTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository users;

    @Autowired
    JwtEncoder jwtEncoder;

    TestTokens tokens;
    String employeeToken;
    String managerToken;
    String adminToken;

    @BeforeEach
    void setUp() {
        tokens = new TestTokens(jwtEncoder);
        User alice = users.save(new User("alice", "hash-that-must-not-leak", "Alice", Role.EMPLOYEE));
        User bob = users.save(new User("bob", "hash-that-must-not-leak", "Bob", Role.MANAGER));
        User dave = users.save(new User("dave", "hash-that-must-not-leak", "Dave", Role.ADMIN));

        employeeToken = tokens.forUser(alice);
        managerToken = tokens.forUser(bob);
        adminToken = tokens.forUser(dave);
    }

    @Test
    void adminCanListUsers() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].username", hasItem("alice")));
    }

    @Test
    void employeeCannotListUsers() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerCannotListUsers() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void responseNeverContainsPasswordHash() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("passwordHash"))))
                .andExpect(content().string(not(containsString("hash-that-must-not-leak"))));
    }
}
