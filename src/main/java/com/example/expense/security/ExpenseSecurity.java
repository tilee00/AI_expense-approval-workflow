package com.example.expense.security;

import com.example.expense.expense.Expense;
import com.example.expense.expense.ExpenseRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component("expenseSecurity")
@Transactional(readOnly = true)
public class ExpenseSecurity {

    private final ExpenseRepository expenses;

    public ExpenseSecurity(ExpenseRepository expenses) {
        this.expenses = expenses;
    }

    public boolean isOwner(Long expenseId, Authentication authentication) {
        return expenses.findById(expenseId)
                .map(expense -> isOwnerOf(expense, authentication))
                .orElse(false);
    }

    public boolean canView(Long expenseId, Authentication authentication) {
        return expenses.findById(expenseId)
                .map(expense -> isOwnerOf(expense, authentication)
                        || hasAnyRole(authentication, "ROLE_MANAGER", "ROLE_FINANCE", "ROLE_ADMIN"))
                .orElse(false);
    }

    public boolean canEdit(Long expenseId, Authentication authentication) {
        return isOwner(expenseId, authentication);
    }

    private boolean isOwnerOf(Expense expense, Authentication authentication) {
        return expense.getOwner().getUsername().equals(authentication.getName());
    }

    private boolean hasAnyRole(Authentication authentication, String... roles) {
        List<String> wanted = List.of(roles);
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> wanted.contains(authority.getAuthority()));
    }
}
