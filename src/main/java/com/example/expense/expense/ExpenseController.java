package com.example.expense.expense;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @PostMapping
    ResponseEntity<ExpenseResponse> create(@Valid @RequestBody ExpenseRequest request,
                                           Authentication authentication) {
        ExpenseResponse created = expenseService.create(request, authentication);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/mine")
    List<ExpenseResponse> mine(Authentication authentication) {
        return expenseService.listMine(authentication);
    }

    @GetMapping
    List<ExpenseResponse> all() {
        return expenseService.listAll();
    }

    @GetMapping("/{id}")
    ExpenseResponse get(@PathVariable Long id) {
        return expenseService.get(id);
    }

    @PutMapping("/{id}")
    ExpenseResponse update(@PathVariable Long id, @Valid @RequestBody ExpenseRequest request) {
        return expenseService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        expenseService.delete(id);
    }

    @PostMapping("/{id}/submit")
    ExpenseResponse submit(@PathVariable Long id) {
        return expenseService.submit(id);
    }

    @PostMapping("/{id}/approve")
    ExpenseResponse approve(@PathVariable Long id, Authentication authentication) {
        return expenseService.approve(id, authentication);
    }

    @PostMapping("/{id}/reject")
    ExpenseResponse reject(@PathVariable Long id, Authentication authentication) {
        return expenseService.reject(id, authentication);
    }

    @PostMapping("/{id}/pay")
    ExpenseResponse pay(@PathVariable Long id) {
        return expenseService.pay(id);
    }
}
