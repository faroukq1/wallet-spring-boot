package com.wallet.wallet.service;

import com.wallet.wallet.entity.Account;
import com.wallet.wallet.entity.AccountStatus;
import com.wallet.wallet.repository.AccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TransferServiceConcurrencyTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    private Long sourceId;
    private Long destId;

    // ==========================================================
    // SETUP: Create two fresh accounts before each test
    // ==========================================================
    @BeforeEach
    void setUp() {
        Account source = new Account();
        source.setBalance(new BigDecimal("1000.00"));
        source.setStatus(AccountStatus.ACTIVE);
        source.setOwnerId(900L);
        sourceId = accountRepository.save(source).getId();

        Account dest = new Account();
        dest.setBalance(new BigDecimal("1000.00"));
        dest.setStatus(AccountStatus.ACTIVE);
        dest.setOwnerId(901L);
        destId = accountRepository.save(dest).getId();

        System.out.println("Setup: source=" + sourceId + ", dest=" + destId);
    }

    @AfterEach
    void tearDown() {
        accountRepository.deleteById(sourceId);
        accountRepository.deleteById(destId);
    }

    // ==========================================================
    // TEST 1: 10 concurrent withdrawals on the SAME account
    // Expected: all 10 succeed, final balance = 0
    // ==========================================================
    @Test
    void concurrentWithdrawals_shouldNotLoseUpdates() throws Exception {
        int threadCount = 10;
        BigDecimal withdrawAmount = new BigDecimal("100.00");

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threadCount);

        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await(); // wait for the signal
                    // isAdmin=true: this test exercises concurrency, not ownership checks.
                    accountService.withdraw(sourceId, withdrawAmount, "concurrency-test", true);
                    successes.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                    System.err.println("Withdrawal failed: " + e.getMessage());
                } finally {
                    doneGate.countDown();
                }
            });
        }

        // 🔥 FIRE ALL THREADS AT ONCE
        startGate.countDown();

        boolean completed = doneGate.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Threads did not complete in time");

        Account finalAccount = accountRepository.findById(sourceId).orElseThrow();
        System.out.println(">>> Final balance: " + finalAccount.getBalance());
        System.out.println(">>> Successes: " + successes.get() + ", Failures: " + failures.get());

        // 10 successful withdrawals * 100 = 1000. Balance must be 0.
        assertEquals(0, finalAccount.getBalance().compareTo(new BigDecimal("0.00")),
                "Balance should be exactly 0 after 10x100 withdrawals");
        assertEquals(10, successes.get(), "All 10 withdrawals should succeed");
        assertEquals(0, failures.get(), "No withdrawal should fail");
    }

    // ==========================================================
    // TEST 2: Opposing transfers in parallel (deadlock test)
    // 5 threads A->B, 5 threads B->A. Net zero change.
    // ==========================================================
    @Test
    void opposingTransfers_shouldNotDeadlock() throws Exception {
        int transfersPerDirection = 5;
        int totalThreads = transfersPerDirection * 2;
        BigDecimal amount = new BigDecimal("100.00");

        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(totalThreads);

        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < transfersPerDirection; i++) {
            // A -> B thread
            executor.submit(() -> {
                try {
                    startGate.await();
                    transferService.transfer(sourceId, destId, amount, "concurrency-test", true);
                } catch (Exception e) {
                    failures.incrementAndGet();
                    System.err.println("A->B failed: " + e.getMessage());
                } finally {
                    doneGate.countDown();
                }
            });
            // B -> A thread
            executor.submit(() -> {
                try {
                    startGate.await();
                    transferService.transfer(destId, sourceId, amount, "concurrency-test", true);
                } catch (Exception e) {
                    failures.incrementAndGet();
                    System.err.println("B->A failed: " + e.getMessage());
                } finally {
                    doneGate.countDown();
                }
            });
        }

        // 🔥 FIRE ALL THREADS AT ONCE
        startGate.countDown();

        // If deadlock, this will timeout at 60s and fail
        boolean completed = doneGate.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "🚨 DEADLOCK DETECTED - transfers did not complete in time");

        Account source = accountRepository.findById(sourceId).orElseThrow();
        Account dest = accountRepository.findById(destId).orElseThrow();

        System.out.println(">>> Source balance: " + source.getBalance());
        System.out.println(">>> Dest balance: " + dest.getBalance());

        // 5 transfers out and 5 transfers in = net zero
        assertEquals(0, source.getBalance().compareTo(new BigDecimal("1000.00")),
                "Source balance should be unchanged (1000.00)");
        assertEquals(0, dest.getBalance().compareTo(new BigDecimal("1000.00")),
                "Dest balance should be unchanged (1000.00)");
        assertEquals(0, failures.get(), "No transfer should fail");
    }

    // ==========================================================
    // TEST 3: Withdrawals AND transfers racing on the SAME source
    // 5 threads withdraw 100, 5 threads transfer 100 to dest.
    // Expected: all succeed, source exactly 0, dest exactly 1500.
    // ==========================================================
    @Test
    void mixedWithdrawalsAndTransfers_shouldStayConsistent() throws Exception {
        int withdrawThreads = 5;
        int transferThreads = 5;
        BigDecimal amount = new BigDecimal("100.00");

        ExecutorService executor = Executors.newFixedThreadPool(withdrawThreads + transferThreads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(withdrawThreads + transferThreads);
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < withdrawThreads; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    accountService.withdraw(sourceId, amount, "concurrency-test", true);
                } catch (Exception e) {
                    failures.incrementAndGet();
                    System.err.println("Mixed withdrawal failed: " + e.getMessage());
                } finally {
                    doneGate.countDown();
                }
            });
        }
        for (int i = 0; i < transferThreads; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    transferService.transfer(sourceId, destId, amount, "concurrency-test", true);
                } catch (Exception e) {
                    failures.incrementAndGet();
                    System.err.println("Mixed transfer failed: " + e.getMessage());
                } finally {
                    doneGate.countDown();
                }
            });
        }

        // 🔥 FIRE ALL THREADS AT ONCE
        startGate.countDown();

        boolean completed = doneGate.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(completed, "Threads did not complete in time");

        Account source = accountRepository.findById(sourceId).orElseThrow();
        Account dest = accountRepository.findById(destId).orElseThrow();

        assertEquals(0, source.getBalance().compareTo(new BigDecimal("0.00")),
                "5x100 withdrawals + 5x100 transfers should drain the source exactly");
        assertEquals(0, dest.getBalance().compareTo(new BigDecimal("1500.00")),
                "Destination should receive exactly 5x100 transfers");
        assertEquals(0, failures.get(), "All operations should succeed");
    }
}