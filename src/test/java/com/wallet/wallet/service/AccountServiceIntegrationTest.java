package com.wallet.wallet.service;

import com.wallet.wallet.entity.Account;
import com.wallet.wallet.entity.AccountStatus;
import com.wallet.wallet.entity.Transaction;
import com.wallet.wallet.entity.TransactionStatus;
import com.wallet.wallet.entity.TransactionType;
import com.wallet.wallet.exception.AccountBlockedException;
import com.wallet.wallet.exception.InsufficientBalanceException;
import com.wallet.wallet.repository.AccountRepository;
import com.wallet.wallet.repository.TransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that operations rejected by business rules leave a FAILED trace
 * that survives the rollback of the main transaction (REQUIRES_NEW).
 */
@SpringBootTest
class AccountServiceIntegrationTest {

    @Autowired
    private AccountService accountService;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private TransactionRepository transactionRepository;

    private Long accountId;

    @BeforeEach
    void setUp() {
        Account account = new Account();
        account.setBalance(new BigDecimal("500.00"));
        account.setStatus(AccountStatus.ACTIVE);
        account.setOwnerId(1L); // alice
        accountId = accountRepository.save(account).getId();
    }

    @AfterEach
    void tearDown() {
        accountRepository.deleteById(accountId);
    }

    private List<Transaction> history(Long id) {
        return transactionRepository.findBySourceAccountIdOrDestinationAccountId(id, id);
    }

    @Test
    void withdraw_insufficientBalance_recordsFailedTraceThatSurvivesRollback() {
        BigDecimal amount = new BigDecimal("999.99");
        assertThrows(InsufficientBalanceException.class,
                () -> accountService.withdraw(accountId, amount, "alice", false));

        assertEquals(0, new BigDecimal("500.00")
                .compareTo(accountRepository.findById(accountId).orElseThrow().getBalance()));

        boolean failedTrace = history(accountId).stream()
                .anyMatch(tx -> tx.getType() == TransactionType.WITHDRAW
                        && tx.getStatus() == TransactionStatus.FAILED
                        && tx.getSourceAccountId().equals(accountId)
                        && tx.getAmount().compareTo(amount) == 0);
        assertTrue(failedTrace, "A FAILED WITHDRAW trace should be committed despite the rollback");
    }

    @Test
    void deposit_onBlockedAccount_recordsFailedTrace() {
        Account account = accountRepository.findById(accountId).orElseThrow();
        account.setStatus(AccountStatus.BLOCKED);
        accountRepository.save(account);

        BigDecimal amount = new BigDecimal("50.00");
        assertThrows(AccountBlockedException.class,
                () -> accountService.deposit(accountId, amount, "alice", false));

        boolean failedTrace = history(accountId).stream()
                .anyMatch(tx -> tx.getType() == TransactionType.DEPOSIT
                        && tx.getStatus() == TransactionStatus.FAILED
                        && tx.getDestinationAccountId().equals(accountId));
        assertTrue(failedTrace, "A FAILED DEPOSIT trace should be recorded for a blocked account");
    }

    @Test
    void successfulWithdraw_doesNotRecordFailedTrace() {
        accountService.withdraw(accountId, new BigDecimal("100.00"), "alice", false);

        boolean anyFailed = history(accountId).stream()
                .anyMatch(tx -> tx.getStatus() == TransactionStatus.FAILED);
        assertTrue(!anyFailed, "A successful operation must not leave a FAILED trace");
    }
}
