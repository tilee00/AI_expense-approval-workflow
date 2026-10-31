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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduces two requests deciding the same expense at the same time. A
 * read-then-write check lets both commit; the last write wins and the caller
 * still gets 200. With optimistic locking exactly one commit survives.
 *
 * Deliberately NOT @Transactional: the two writers must commit independently.
 */
@SpringBootTest(properties = {
        "app.seed=false",
        "spring.datasource.url=jdbc:h2:mem:expenses-versioning;DB_CLOSE_DELAY=-1"
})
class ExpenseVersioningTest {

    @Autowired
    UserRepository users;

    @Autowired
    ExpenseRepository expenses;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void twoConcurrentWritesToOneExpenseLeaveExactlyOneWinner() throws Exception {
        expenses.deleteAll();
        users.deleteAll();

        User owner = users.save(new User("version-alice", "x", "Alice", Role.EMPLOYEE));
        Expense draft = expenses.save(new Expense(owner, new BigDecimal("10.00"), "EUR", "Taxi"));

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CyclicBarrier bothLoaded = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> approve = () -> {
                try {
                    transaction.executeWithoutResult(status -> {
                        Expense expense = expenses.findById(draft.getId()).orElseThrow();
                        await(bothLoaded);
                        expense.setStatus(ExpenseStatus.APPROVED);
                    });
                    return true;
                } catch (RuntimeException conflict) {
                    return false;
                }
            };

            Future<Boolean> first = pool.submit(approve);
            Future<Boolean> second = pool.submit(approve);

            int winners = (first.get(30, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(30, TimeUnit.SECONDS) ? 1 : 0);

            assertThat(winners)
                    .as("exactly one concurrent write may commit")
                    .isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("both writers failed to reach the barrier", e);
        }
    }
}
