package com.example.expense.expense;

import java.math.BigDecimal;
import java.time.Instant;

public record ExpenseResponse(
        Long id,
        String owner,
        BigDecimal amount,
        String currency,
        String description,
        ExpenseStatus status,
        Instant submittedAt,
        Instant decidedAt,
        String decidedBy,
        Instant paidAt) {

    public static ExpenseResponse from(Expense expense) {
        return new ExpenseResponse(
                expense.getId(),
                expense.getOwner().getUsername(),
                expense.getAmount(),
                expense.getCurrency(),
                expense.getDescription(),
                expense.getStatus(),
                expense.getSubmittedAt(),
                expense.getDecidedAt(),
                expense.getDecidedBy() == null ? null : expense.getDecidedBy().getUsername(),
                expense.getPaidAt());
    }
}
