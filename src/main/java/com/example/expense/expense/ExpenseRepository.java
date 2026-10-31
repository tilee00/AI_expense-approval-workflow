package com.example.expense.expense;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByOwnerUsernameOrderByIdAsc(String username);

    List<Expense> findAllByOrderByIdAsc();
}
