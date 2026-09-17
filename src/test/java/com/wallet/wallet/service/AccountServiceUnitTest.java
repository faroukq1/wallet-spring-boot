package com.wallet.wallet.service;

import com.wallet.wallet.dto.AccountResponse;
import com.wallet.wallet.entity.Account;
import com.wallet.wallet.entity.AccountStatus;
import com.wallet.wallet.entity.Transaction;
import com.wallet.wallet.entity.TransactionStatus;
import com.wallet.wallet.entity.TransactionType;
import com.wallet.wallet.event.OperationCompletedEvent;
import com.wallet.wallet.exception.AccountBlockedException;
import com.wallet.wallet.exception.AccountNotFoundException;
import com.wallet.wallet.exception.InsufficientBalanceException;
import com.wallet.wallet.exception.UnauthorizedAccessException;
import com.wallet.wallet.repository.AccountRepository;
import com.wallet.wallet.repository.TransactionRepository;
import com.wallet.wallet.security.CustomUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountServiceUnitTest {

    private static final Long ACCOUNT_ID = 1L;

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private CustomUserDetailsService userDetailsService;

    private AccountService accountService;
    private Account account;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository, transactionRepository, eventPublisher, userDetailsService);

        account = new Account();
        account.setId(ACCOUNT_ID);
        account.setBalance(new BigDecimal("1000.00"));
        account.setStatus(AccountStatus.ACTIVE);
        account.setOwnerId(1L);

        when(accountRepository.findWithLockById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(userDetailsService.getOwnerIdForUsername("alice")).thenReturn(1L);
    }

    @Test
    void deposit_asOwner_increasesBalanceAndRecordsTransaction() {
        AccountResponse response = accountService.deposit(ACCOUNT_ID, new BigDecimal("250.00"), "alice", false);

        assertEquals(0, new BigDecimal("1250.00").compareTo(account.getBalance()));
        assertEquals(ACCOUNT_ID, response.id());
        assertEquals(0, new BigDecimal("1250.00").compareTo(response.balance()));

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction tx = captor.getValue();
        assertEquals(TransactionType.DEPOSIT, tx.getType());
        assertEquals(TransactionStatus.SUCCESS, tx.getStatus());
        assertEquals(ACCOUNT_ID, tx.getDestinationAccountId());
        assertEquals(0, new BigDecimal("250.00").compareTo(tx.getAmount()));

        ArgumentCaptor<OperationCompletedEvent> eventCaptor = ArgumentCaptor.forClass(OperationCompletedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(TransactionType.DEPOSIT, eventCaptor.getValue().operationType());
        assertEquals(ACCOUNT_ID, eventCaptor.getValue().destinationAccountId());
    }

    @Test
    void deposit_asAdmin_skipsOwnershipCheck() {
        accountService.deposit(ACCOUNT_ID, new BigDecimal("10.00"), "admin", true);

        assertEquals(0, new BigDecimal("1010.00").compareTo(account.getBalance()));
        verify(userDetailsService, never()).getOwnerIdForUsername(anyString());
    }

    @Test
    void withdraw_success_decreasesBalanceAndRecordsTransaction() {
        AccountResponse response = accountService.withdraw(ACCOUNT_ID, new BigDecimal("400.00"), "alice", false);

        assertEquals(0, new BigDecimal("600.00").compareTo(account.getBalance()));
        assertEquals(0, new BigDecimal("600.00").compareTo(response.balance()));

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction tx = captor.getValue();
        assertEquals(TransactionType.WITHDRAW, tx.getType());
        assertEquals(TransactionStatus.SUCCESS, tx.getStatus());
        assertEquals(ACCOUNT_ID, tx.getSourceAccountId());
        assertEquals(0, new BigDecimal("400.00").compareTo(tx.getAmount()));

        ArgumentCaptor<OperationCompletedEvent> eventCaptor = ArgumentCaptor.forClass(OperationCompletedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(TransactionType.WITHDRAW, eventCaptor.getValue().operationType());
        assertEquals(ACCOUNT_ID, eventCaptor.getValue().sourceAccountId());
    }

    @Test
    void withdraw_insufficientBalance_throwsAndChangesNothing() {
        assertThrows(InsufficientBalanceException.class,
                () -> accountService.withdraw(ACCOUNT_ID, new BigDecimal("1000.01"), "alice", false));

        assertEquals(0, new BigDecimal("1000.00").compareTo(account.getBalance()));
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void withdraw_exactBalance_succeeds() {
        AccountResponse response = accountService.withdraw(ACCOUNT_ID, new BigDecimal("1000.00"), "alice", false);

        assertEquals(0, new BigDecimal("0.00").compareTo(response.balance()));
        assertEquals(0, new BigDecimal("0.00").compareTo(account.getBalance()));
    }

    @Test
    void deposit_onBlockedAccount_throws() {
        account.setStatus(AccountStatus.BLOCKED);

        assertThrows(AccountBlockedException.class,
                () -> accountService.deposit(ACCOUNT_ID, new BigDecimal("10.00"), "alice", false));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void withdraw_fromBlockedAccount_throws() {
        account.setStatus(AccountStatus.BLOCKED);

        assertThrows(AccountBlockedException.class,
                () -> accountService.withdraw(ACCOUNT_ID, new BigDecimal("10.00"), "alice", false));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void deposit_missingAccount_throws() {
        when(accountRepository.findWithLockById(99L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class,
                () -> accountService.deposit(99L, new BigDecimal("10.00"), "alice", false));
    }

    @Test
    void getAccount_missingAccount_throws() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class,
                () -> accountService.getAccount(99L, "alice", false));
    }

    @Test
    void getAccount_returnsBalanceAndStatus() {
        AccountResponse response = accountService.getAccount(ACCOUNT_ID, "alice", false);

        assertEquals(ACCOUNT_ID, response.id());
        assertEquals(0, new BigDecimal("1000.00").compareTo(response.balance()));
        assertEquals("ACTIVE", response.status());
    }

    @Test
    void getAccount_nonOwner_throws() {
        when(userDetailsService.getOwnerIdForUsername("mallory")).thenReturn(5L);

        assertThrows(UnauthorizedAccessException.class,
                () -> accountService.getAccount(ACCOUNT_ID, "mallory", false));
    }

    @Test
    void getHistory_returnsTransactions() {
        Transaction tx = new Transaction();
        tx.setId(7L);
        tx.setType(TransactionType.DEPOSIT);
        tx.setAmount(new BigDecimal("10.00"));
        when(transactionRepository.findBySourceAccountIdOrDestinationAccountId(ACCOUNT_ID, ACCOUNT_ID))
                .thenReturn(List.of(tx));

        List<Transaction> history = accountService.getHistory(ACCOUNT_ID, "alice", false);

        assertEquals(1, history.size());
        assertSame(tx, history.get(0));
    }

    @Test
    void getHistory_nonOwner_throws() {
        when(userDetailsService.getOwnerIdForUsername("mallory")).thenReturn(5L);

        assertThrows(UnauthorizedAccessException.class,
                () -> accountService.getHistory(ACCOUNT_ID, "mallory", false));
    }
}
