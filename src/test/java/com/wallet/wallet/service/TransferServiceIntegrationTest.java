package com.wallet.wallet.service;

import com.wallet.wallet.entity.Account;
import com.wallet.wallet.entity.AccountStatus;
import com.wallet.wallet.entity.Transaction;
import com.wallet.wallet.entity.TransactionType;
import com.wallet.wallet.exception.AccountBlockedException;
import com.wallet.wallet.exception.AccountNotFoundException;
import com.wallet.wallet.exception.InsufficientBalanceException;
import com.wallet.wallet.exception.InvalidOperationException;
import com.wallet.wallet.exception.UnauthorizedAccessException;
import com.wallet.wallet.repository.AccountRepository;
import com.wallet.wallet.repository.TransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class TransferServiceIntegrationTest {

    private static final Long MISSING_ID = 999_999L;

    @Autowired
    private TransferService transferService;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private TransactionRepository transactionRepository;

    private Long sourceId;
    private Long destId;
    private Long blockedId;
    private Long aliceId;

    @BeforeEach
    void setUp() {
        sourceId = saveAccount("500.00", 900L, AccountStatus.ACTIVE);
        destId = saveAccount("100.00", 901L, AccountStatus.ACTIVE);
        blockedId = saveAccount("50.00", 902L, AccountStatus.BLOCKED);
        aliceId = saveAccount("1000.00", 1L, AccountStatus.ACTIVE);
    }

    @AfterEach
    void tearDown() {
        accountRepository.deleteById(sourceId);
        accountRepository.deleteById(destId);
        accountRepository.deleteById(blockedId);
        accountRepository.deleteById(aliceId);
    }

    private Long saveAccount(String balance, Long ownerId, AccountStatus status) {
        Account account = new Account();
        account.setBalance(new BigDecimal(balance));
        account.setOwnerId(ownerId);
        account.setStatus(status);
        return accountRepository.save(account).getId();
    }

    private BigDecimal balance(Long id) {
        return accountRepository.findById(id).orElseThrow().getBalance();
    }

    @Test
    void transfer_movesMoneyAndRecordsAuditLog() {
        transferService.transfer(sourceId, destId, new BigDecimal("200.00"), "integration", true);

        assertEquals(0, new BigDecimal("300.00").compareTo(balance(sourceId)));
        assertEquals(0, new BigDecimal("300.00").compareTo(balance(destId)));

        boolean recorded = transactionRepository
                .findBySourceAccountIdOrDestinationAccountId(sourceId, destId).stream()
                .anyMatch(tx -> tx.getType() == TransactionType.TRANSFER
                        && sourceId.equals(tx.getSourceAccountId())
                        && destId.equals(tx.getDestinationAccountId())
                        && tx.getAmount().compareTo(new BigDecimal("200.00")) == 0);
        assertTrue(recorded, "A TRANSFER audit row should be recorded");
    }

    @Test
    void transfer_asOwner_succeeds() {
        transferService.transfer(aliceId, destId, new BigDecimal("100.00"), "alice", false);

        assertEquals(0, new BigDecimal("900.00").compareTo(balance(aliceId)));
        assertEquals(0, new BigDecimal("200.00").compareTo(balance(destId)));
    }

    @Test
    void transfer_toSameAccount_throwsInvalidOperation() {
        assertThrows(InvalidOperationException.class,
                () -> transferService.transfer(sourceId, sourceId, new BigDecimal("10.00"), "integration", true));

        assertEquals(0, new BigDecimal("500.00").compareTo(balance(sourceId)));
    }

    @Test
    void transfer_insufficientBalance_throwsAndRollsBack() {
        assertThrows(InsufficientBalanceException.class,
                () -> transferService.transfer(sourceId, destId, new BigDecimal("500.01"), "integration", true));

        assertEquals(0, new BigDecimal("500.00").compareTo(balance(sourceId)));
        assertEquals(0, new BigDecimal("100.00").compareTo(balance(destId)));
    }

    @Test
    void transfer_fromBlockedSource_throws() {
        assertThrows(AccountBlockedException.class,
                () -> transferService.transfer(blockedId, destId, new BigDecimal("10.00"), "integration", true));

        assertEquals(0, new BigDecimal("50.00").compareTo(balance(blockedId)));
        assertEquals(0, new BigDecimal("100.00").compareTo(balance(destId)));
    }

    @Test
    void transfer_toBlockedDestination_throws() {
        assertThrows(AccountBlockedException.class,
                () -> transferService.transfer(sourceId, blockedId, new BigDecimal("10.00"), "integration", true));

        assertEquals(0, new BigDecimal("500.00").compareTo(balance(sourceId)));
        assertEquals(0, new BigDecimal("50.00").compareTo(balance(blockedId)));
    }

    @Test
    void transfer_missingSource_throwsAccountNotFound() {
        assertThrows(AccountNotFoundException.class,
                () -> transferService.transfer(MISSING_ID, destId, new BigDecimal("10.00"), "integration", true));
    }

    @Test
    void transfer_missingDestination_throwsAccountNotFound() {
        assertThrows(AccountNotFoundException.class,
                () -> transferService.transfer(sourceId, MISSING_ID, new BigDecimal("10.00"), "integration", true));
    }

    @Test
    void transfer_nonOwner_throwsUnauthorized() {
        assertThrows(UnauthorizedAccessException.class,
                () -> transferService.transfer(sourceId, destId, new BigDecimal("10.00"), "alice", false));

        assertEquals(0, new BigDecimal("500.00").compareTo(balance(sourceId)));
    }
}
