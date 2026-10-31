package com.example.expense;

import com.example.expense.expense.Expense;
import com.example.expense.expense.ExpenseRepository;
import com.example.expense.expense.ExpenseStatus;
import com.example.expense.user.Role;
import com.example.expense.user.User;
import com.example.expense.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.seed=false")
@Transactional
class DomainModelTest {

    @Autowired
    UserRepository users;

    @Autowired
    ExpenseRepository expenses;

    @Test
    void savesUserWithRolesAndExpenseOwnedByThem() {
        User alice = users.save(new User("alice", "x", "Alice", Role.EMPLOYEE));
        Expense saved = expenses.save(new Expense(alice, new BigDecimal("42.50"), "EUR", "Taxi"));

        Expense found = expenses.findById(saved.getId()).orElseThrow();
        assertThat(found.getOwner().getUsername()).isEqualTo("alice");
        assertThat(found.getStatus()).isEqualTo(ExpenseStatus.DRAFT);
        assertThat(users.findByUsername("alice").orElseThrow().getRoles())
                .containsExactly(Role.EMPLOYEE);
    }
}
