package com.example.expense.expense;

import com.example.expense.common.ConflictException;
import com.example.expense.common.NotFoundException;
import com.example.expense.user.User;
import com.example.expense.user.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class ExpenseService {

    private final ExpenseRepository expenses;
    private final UserRepository users;

    public ExpenseService(ExpenseRepository expenses, UserRepository users) {
        this.expenses = expenses;
        this.users = users;
    }

    @PreAuthorize("isAuthenticated()")
    public ExpenseResponse create(ExpenseRequest request, Authentication authentication) {
        User owner = currentUser(authentication);
        Expense expense = new Expense(owner, request.amount(), request.currency(), request.description());
        return ExpenseResponse.from(expenses.save(expense));
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<ExpenseResponse> listMine(Authentication authentication) {
        return expenses.findByOwnerUsernameOrderByIdAsc(authentication.getName()).stream()
                .map(ExpenseResponse::from)
                .toList();
    }

    @PreAuthorize("hasAnyRole('MANAGER','FINANCE','ADMIN')")
    @Transactional(readOnly = true)
    public List<ExpenseResponse> listAll() {
        return expenses.findAllByOrderByIdAsc().stream()
                .map(ExpenseResponse::from)
                .toList();
    }

    @PreAuthorize("@expenseSecurity.canView(#id, authentication)")
    @Transactional(readOnly = true)
    public ExpenseResponse get(Long id) {
        return ExpenseResponse.from(find(id));
    }

    @PreAuthorize("@expenseSecurity.canEdit(#id, authentication)")
    public ExpenseResponse update(Long id, ExpenseRequest request) {
        Expense expense = find(id);
        requireStatus(expense, ExpenseStatus.DRAFT);
        expense.setAmount(request.amount());
        expense.setCurrency(request.currency());
        expense.setDescription(request.description());
        return ExpenseResponse.from(expense);
    }

    @PreAuthorize("@expenseSecurity.canEdit(#id, authentication)")
    public void delete(Long id) {
        Expense expense = find(id);
        requireStatus(expense, ExpenseStatus.DRAFT);
        expenses.delete(expense);
    }

    @PreAuthorize("@expenseSecurity.isOwner(#id, authentication)")
    public ExpenseResponse submit(Long id) {
        Expense expense = find(id);
        requireStatus(expense, ExpenseStatus.DRAFT);
        expense.setStatus(ExpenseStatus.SUBMITTED);
        expense.setSubmittedAt(Instant.now());
        return ExpenseResponse.from(expense);
    }

    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ExpenseResponse approve(Long id, Authentication authentication) {
        Expense expense = find(id);
        requireStatus(expense, ExpenseStatus.SUBMITTED);
        expense.setStatus(ExpenseStatus.APPROVED);
        expense.setDecidedAt(Instant.now());
        expense.setDecidedBy(currentUser(authentication));
        return ExpenseResponse.from(expense);
    }

    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ExpenseResponse reject(Long id, Authentication authentication) {
        Expense expense = find(id);
        requireStatus(expense, ExpenseStatus.SUBMITTED);
        expense.setStatus(ExpenseStatus.REJECTED);
        expense.setDecidedAt(Instant.now());
        expense.setDecidedBy(currentUser(authentication));
        return ExpenseResponse.from(expense);
    }

    @PreAuthorize("hasAnyRole('FINANCE','ADMIN')")
    public ExpenseResponse pay(Long id) {
        Expense expense = find(id);
        requireStatus(expense, ExpenseStatus.APPROVED);
        expense.setStatus(ExpenseStatus.PAID);
        expense.setPaidAt(Instant.now());
        return ExpenseResponse.from(expense);
    }

    private Expense find(Long id) {
        return expenses.findById(id)
                .orElseThrow(() -> new NotFoundException("Expense " + id + " not found"));
    }

    private User currentUser(Authentication authentication) {
        return users.findByUsername(authentication.getName())
                .orElseThrow(() -> new NotFoundException("User " + authentication.getName() + " not found"));
    }

    private void requireStatus(Expense expense, ExpenseStatus expected) {
        if (expense.getStatus() != expected) {
            throw new ConflictException("Expense is " + expense.getStatus() + ", expected " + expected);
        }
    }
}
