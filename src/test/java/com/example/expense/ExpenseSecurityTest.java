package com.example.expense;

import com.example.expense.expense.Expense;
import com.example.expense.expense.ExpenseRepository;
import com.example.expense.security.ExpenseSecurity;
import com.example.expense.user.Role;
import com.example.expense.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExpenseSecurityTest {

    private final ExpenseRepository expenses = mock(ExpenseRepository.class);
    private final User alice = new User("alice", "x", "Alice", Role.EMPLOYEE);
    private final Expense expense = new Expense(alice, new BigDecimal("10.00"), "EUR", "Taxi");

    private final Authentication aliceAuth =
            new TestingAuthenticationToken("alice", null, "ROLE_EMPLOYEE");
    private final Authentication strangerAuth =
            new TestingAuthenticationToken("erin", null, "ROLE_EMPLOYEE");
    private final Authentication managerAuth =
            new TestingAuthenticationToken("bob", null, "ROLE_MANAGER");

    private ExpenseSecurity security;

    @BeforeEach
    void setUp() {
        security = new ExpenseSecurity(expenses);
        when(expenses.findById(1L)).thenReturn(Optional.of(expense));
        when(expenses.findById(99L)).thenReturn(Optional.empty());
    }

    @Test
    void ownerCanEditOwnExpense() {
        assertThat(security.canEdit(1L, aliceAuth)).isTrue();
    }

    @Test
    void strangerCannotEdit() {
        assertThat(security.canEdit(1L, strangerAuth)).isFalse();
    }

    @Test
    void managerCanViewButNotEdit() {
        assertThat(security.canView(1L, managerAuth)).isTrue();
        assertThat(security.canEdit(1L, managerAuth)).isFalse();
    }

    @Test
    void managerCannotEdit() {
        assertThat(security.canEdit(1L, managerAuth)).isFalse();
    }

    @Test
    void missingExpenseDeniesEveryone() {
        assertThat(security.canView(99L, managerAuth)).isFalse();
        assertThat(security.canEdit(99L, aliceAuth)).isFalse();
    }

    @Test
    void employeeCannotViewSomeoneElsesExpense() {
        assertThat(security.canView(1L, strangerAuth)).isFalse();
    }
}
