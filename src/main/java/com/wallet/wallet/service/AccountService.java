package com.wallet.wallet.service;

import com.wallet.wallet.dto.AccountResponse;
import com.wallet.wallet.entity.Account;
import com.wallet.wallet.entity.AccountStatus;
import com.wallet.wallet.entity.Transaction;
import com.wallet.wallet.entity.TransactionStatus;
import com.wallet.wallet.entity.TransactionType;
import com.wallet.wallet.exception.AccountBlockedException;
import com.wallet.wallet.exception.AccountNotFoundException;
import com.wallet.wallet.exception.InactiveAccountException;
import com.wallet.wallet.exception.InsufficientBalanceException;
import com.wallet.wallet.exception.UnauthorizedAccessException;
import com.wallet.wallet.repository.AccountRepository;
import com.wallet.wallet.repository.TransactionRepository;
import com.wallet.wallet.security.CustomUserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.wallet.wallet.event.OperationCompletedEvent;
import org.springframework.context.ApplicationEventPublisher;
import java.time.LocalDateTime;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final CustomUserDetailsService userDetailsService;

    // Constructor Injection: Spring provides the repositories.
    public AccountService(AccountRepository accountRepository,
                          TransactionRepository transactionRepository,
                          ApplicationEventPublisher eventPublisher,
                          CustomUserDetailsService userDetailsService
                          ) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.eventPublisher = eventPublisher;
        this.userDetailsService = userDetailsService;
    }

    // deposit logic
    @Transactional
    public AccountResponse deposit (Long accountId, BigDecimal amount, String currentUsername, boolean isAdmin) {
        Account account = accountRepository.findWithLockById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

        validateOwnership(account, accountId, currentUsername, isAdmin);
        validateAccountStatus(account);

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        Transaction tx = new Transaction();
        tx.setType(TransactionType.DEPOSIT);
        tx.setAmount(amount);
        tx.setStatus(TransactionStatus.SUCCESS);
        tx.setDestinationAccountId(accountId);
        transactionRepository.save(tx);


        // NEW: publish deposit event
        eventPublisher.publishEvent(new OperationCompletedEvent(
                TransactionType.DEPOSIT,
                null,
                accountId,
                amount,
                LocalDateTime.now()
        ));
        return new AccountResponse(account.getId(), account.getBalance(), account.getStatus().name());
    }

    // remove money from account
    @Transactional
    public AccountResponse withdraw (Long accountId, BigDecimal amount, String currentUsername, boolean isAdmin) {

        Account account = accountRepository.findWithLockById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

        validateOwnership(account, accountId, currentUsername, isAdmin);
        validateAccountStatus(account);

        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient balance for account " + accountId +
                            ". Available: " + account.getBalance() + ", Requested: " + amount
            );
        }


        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);

        Transaction tx = new Transaction();
        tx.setType(TransactionType.WITHDRAW);
        tx.setAmount(amount);
        tx.setStatus(TransactionStatus.SUCCESS);
        tx.setSourceAccountId(accountId);
        transactionRepository.save(tx);

        // NEW: publish withdraw event
        eventPublisher.publishEvent(new OperationCompletedEvent(
                TransactionType.WITHDRAW,
                accountId,
                null,
                amount,
                LocalDateTime.now()
        ));

        return new AccountResponse(account.getId(), account.getBalance(), account.getStatus().name());
    }

    // just read the balance
    public AccountResponse getAccount (Long accountId, String currentUsername, boolean isAdmin) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

        validateOwnership(account, accountId, currentUsername, isAdmin);
        validateAccountStatus(account);
        return new AccountResponse(account.getId(), account.getBalance(), account.getStatus().name());
    }


    // history of transactions
    public List<Transaction> getHistory (Long accountId, String currentUsername, boolean isAdmin) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

        validateOwnership(account, accountId, currentUsername, isAdmin);
        return transactionRepository.findBySourceAccountIdOrDestinationAccountId(accountId, accountId);
    }

    // helper functions
    private void validateOwnership (Account account, Long accountId, String currentUsername, boolean isAdmin) {
        if (isAdmin) {
            return;
        }
        Long currentUserId = userDetailsService.getOwnerIdForUsername(currentUsername);
        if (currentUserId == null || !account.getOwnerId().equals(currentUserId)) {
            throw new UnauthorizedAccessException("You do not own account " + accountId);
        }
    }

    private void validateAccountStatus (Account account) {
        switch (account.getStatus()) {
            case ACTIVE -> { /*it's fine he can pass */ }
            case BLOCKED -> throw new AccountBlockedException("Account " + account.getId() + " is blocked");
        }
    }
}