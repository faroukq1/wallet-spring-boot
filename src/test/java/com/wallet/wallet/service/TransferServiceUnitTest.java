package com.wallet.wallet.service;

import com.wallet.wallet.entity.Account;
import com.wallet.wallet.entity.AccountStatus;
import com.wallet.wallet.entity.Transaction;
import com.wallet.wallet.entity.TransactionStatus;
import com.wallet.wallet.entity.TransactionType;
import com.wallet.wallet.event.OperationCompletedEvent;
import com.wallet.wallet.exception.AccountBlockedException;
import com.wallet.wallet.exception.AccountNotFoundException;
import com.wallet.wallet.exception.InsufficientBalanceException;
import com.wallet.wallet.exception.InvalidOperationException;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransferServiceUnitTest {

    private static final Long SOURCE_ID = 10L;
    private static final Long DEST_ID = 20L;

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private CustomUserDetailsService userDetailsService;

    private TransferService transferService;
    private final Map<Long, Account> accountsById = new HashMap<>();

    @BeforeEach
    void setUp() {
        transferService = new TransferService(accountRepository, transactionRepository, eventPublisher, userDetailsService);

        accountsById.clear();
        accountsById.put(SOURCE_ID, account(SOURCE_ID, "500.00", 900L, AccountStatus.ACTIVE));
        accountsById.put(DEST_ID, account(DEST_ID, "100.00", 901L, AccountStatus.ACTIVE));

        when(accountRepository.findWithLockById(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(accountsById.get(inv.<Long>getArgument(0))));
    }

    private static Account account(Long id, String balance, Long ownerId, AccountStatus status) {
        Account account = new Account();
        account.setId(id);
        account.setBalance(new BigDecimal(balance));
        account.setOwnerId(ownerId);
        account.setStatus(status);
        return account;
    }

    @Test
    void transfer_asAdmin_movesMoneyAndRecordsTransaction() {
        transferService.transfer(SOURCE_ID, DEST_ID, new BigDecimal("200.00"), "admin", true);

        assertEquals(0, new BigDecimal("300.00").compareTo(accountsById.get(SOURCE_ID).getBalance()));
        assertEquals(0, new BigDecimal("300.00").compareTo(accountsById.get(DEST_ID).getBalance()));

        verify(accountRepository).save(accountsById.get(SOURCE_ID));
        verify(accountRepository).save(accountsById.get(DEST_ID));

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction tx = captor.getValue();
        assertEquals(TransactionType.TRANSFER, tx.getType());
        assertEquals(TransactionStatus.SUCCESS, tx.getStatus());
        assertEquals(SOURCE_ID, tx.getSourceAccountId());
        assertEquals(DEST_ID, tx.getDestinationAccountId());
        assertEquals(0, new BigDecimal("200.00").compareTo(tx.getAmount()));

        ArgumentCaptor<OperationCompletedEvent> eventCaptor = ArgumentCaptor.forClass(OperationCompletedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(TransactionType.TRANSFER, eventCaptor.getValue().operationType());
        assertEquals(SOURCE_ID, eventCaptor.getValue().sourceAccountId());
        assertEquals(DEST_ID, eventCaptor.getValue().destinationAccountId());
    }

    @Test
    void transfer_ownerOfSource_canTransfer() {
        when(userDetailsService.getOwnerIdForUsername("alice")).thenReturn(900L);

        transferService.transfer(SOURCE_ID, DEST_ID, new BigDecimal("50.00"), "alice", false);

        assertEquals(0, new BigDecimal("450.00").compareTo(accountsById.get(SOURCE_ID).getBalance()));
        assertEquals(0, new BigDecimal("150.00").compareTo(accountsById.get(DEST_ID).getBalance()));
    }

    @Test
    void transfer_sourceIdGreaterThanDestId_stillWorks() {
        transferService.transfer(DEST_ID, SOURCE_ID, new BigDecimal("100.00"), "admin", true);

        assertEquals(0, new BigDecimal("600.00").compareTo(accountsById.get(SOURCE_ID).getBalance()));
        assertEquals(0, new BigDecimal("0.00").compareTo(accountsById.get(DEST_ID).getBalance()));
    }

    @Test
    void transfer_toSameAccount_throwsInvalidOperation() {
        assertThrows(InvalidOperationException.class,
                () -> transferService.transfer(SOURCE_ID, SOURCE_ID, new BigDecimal("10.00"), "admin", true));

        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transfer_insufficientBalance_throwsAndChangesNothing() {
        assertThrows(InsufficientBalanceException.class,
                () -> transferService.transfer(SOURCE_ID, DEST_ID, new BigDecimal("501.00"), "admin", true));

        assertEquals(0, new BigDecimal("500.00").compareTo(accountsById.get(SOURCE_ID).getBalance()));
        assertEquals(0, new BigDecimal("100.00").compareTo(accountsById.get(DEST_ID).getBalance()));
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transfer_sourceBlocked_throws() {
        accountsById.get(SOURCE_ID).setStatus(AccountStatus.BLOCKED);

        assertThrows(AccountBlockedException.class,
                () -> transferService.transfer(SOURCE_ID, DEST_ID, new BigDecimal("10.00"), "admin", true));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transfer_destinationBlocked_throws() {
        accountsById.get(DEST_ID).setStatus(AccountStatus.BLOCKED);

        assertThrows(AccountBlockedException.class,
                () -> transferService.transfer(SOURCE_ID, DEST_ID, new BigDecimal("10.00"), "admin", true));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transfer_missingSource_throwsAccountNotFound() {
        assertThrows(AccountNotFoundException.class,
                () -> transferService.transfer(999L, DEST_ID, new BigDecimal("10.00"), "admin", true));
    }

    @Test
    void transfer_missingDestination_throwsAccountNotFound() {
        assertThrows(AccountNotFoundException.class,
                () -> transferService.transfer(SOURCE_ID, 999L, new BigDecimal("10.00"), "admin", true));
    }

    @Test
    void transfer_nonOwner_throwsUnauthorized() {
        when(userDetailsService.getOwnerIdForUsername("mallory")).thenReturn(999L);

        assertThrows(UnauthorizedAccessException.class,
                () -> transferService.transfer(SOURCE_ID, DEST_ID, new BigDecimal("10.00"), "mallory", false));
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }
}
