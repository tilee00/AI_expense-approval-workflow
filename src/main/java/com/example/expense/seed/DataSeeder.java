package com.example.expense.seed;

import com.example.expense.expense.Expense;
import com.example.expense.expense.ExpenseRepository;
import com.example.expense.expense.ExpenseStatus;
import com.example.expense.user.Role;
import com.example.expense.user.User;
import com.example.expense.user.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@ConditionalOnProperty(name = "app.seed", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements CommandLineRunner {

    private final UserRepository users;
    private final ExpenseRepository expenses;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository users, ExpenseRepository expenses, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.expenses = expenses;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (users.count() > 0) {
            return;
        }

        User alice = users.save(new User("alice", passwordEncoder.encode("password123"), "Alice", Role.EMPLOYEE));
        User bob = users.save(new User("bob", passwordEncoder.encode("password123"), "Bob", Role.MANAGER));
        User carol = users.save(new User("carol", passwordEncoder.encode("password123"), "Carol", Role.FINANCE));
        User dave = users.save(new User("dave", passwordEncoder.encode("password123"), "Dave", Role.ADMIN));

        expenses.save(new Expense(alice, new BigDecimal("42.50"), "EUR", "Taxi to client site"));
        expenses.save(new Expense(alice, new BigDecimal("120.00"), "EUR", "Team lunch"));

        Instant threeDaysAgo = Instant.now().minus(3, ChronoUnit.DAYS);

        Expense submitted = new Expense(alice, new BigDecimal("450.00"), "EUR", "Conference ticket");
        submitted.setStatus(ExpenseStatus.SUBMITTED);
        submitted.setSubmittedAt(threeDaysAgo);
        expenses.save(submitted);

        Expense approved = new Expense(alice, new BigDecimal("380.00"), "EUR", "Hotel in Berlin");
        approved.setStatus(ExpenseStatus.APPROVED);
        approved.setSubmittedAt(threeDaysAgo);
        approved.setDecidedAt(Instant.now().minus(2, ChronoUnit.DAYS));
        approved.setDecidedBy(bob);
        expenses.save(approved);

        Expense paid = new Expense(alice, new BigDecimal("95.00"), "EUR", "Train ticket");
        paid.setStatus(ExpenseStatus.PAID);
        paid.setSubmittedAt(Instant.now().minus(6, ChronoUnit.DAYS));
        paid.setDecidedAt(Instant.now().minus(5, ChronoUnit.DAYS));
        paid.setDecidedBy(bob);
        paid.setPaidAt(Instant.now().minus(4, ChronoUnit.DAYS));
        expenses.save(paid);
    }
}
