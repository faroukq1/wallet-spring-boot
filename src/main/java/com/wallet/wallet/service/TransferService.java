package com.wallet.wallet.service;

import com.wallet.wallet.entity.*;
import com.wallet.wallet.event.OperationCompletedEvent;
import com.wallet.wallet.event.OperationEventListener;
import com.wallet.wallet.exception.AccountBlockedException;
import com.wallet.wallet.exception.AccountNotFoundException;
import com.wallet.wallet.exception.InsufficientBalanceException;
import com.wallet.wallet.exception.InvalidOperationException;
import com.wallet.wallet.exception.UnauthorizedAccessException;
import com.wallet.wallet.repository.AccountRepository;
import com.wallet.wallet.repository.TransactionRepository;
import com.wallet.wallet.security.CustomUserDetailsService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class TransferService {
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final CustomUserDetailsService userDetailsService;

    public TransferService(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            ApplicationEventPublisher eventPublisher,
            CustomUserDetailsService userDetailsService
            ) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.eventPublisher = eventPublisher;
        this.userDetailsService = userDetailsService;
    }

    @Transactional
    public void transfer(Long sourceId, Long destinationId, BigDecimal amount, String currentUsername, boolean isAdmin) {

        // validate request shape
        if (sourceId.equals(destinationId)) {
            throw new InvalidOperationException("Cannot transfer to the same account: " + sourceId);
        }

        // DEADLOCK problem !!
        // lock the smaller ID first, always. both A -> B and B -> A will
        // follow the same lock order, so no thread can ever hold a lock
        // that the other thread is waiting for.
        Long firstId = Math.min(sourceId, destinationId);
        Long secondId = Math.max(sourceId, destinationId);

        Account firstLocked = accountRepository.findWithLockById(firstId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + firstId));

        Account secondLocked = accountRepository.findWithLockById(secondId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + secondId));

        // mapping step
        // map the locked entities back to their roles
        // we don't know which of firstLocked/secondLocked is the source
        // until we compare the ids
        Account source = sourceId.equals(firstId) ? firstLocked : secondLocked;
        Account destination = destinationId.equals(firstId) ? firstLocked : secondLocked;

        validateOwnership(source, sourceId, currentUsername, isAdmin);

        // business validation
        if (source.getStatus() == AccountStatus.BLOCKED) {
            throw new AccountBlockedException("Source account " + sourceId + " is blocked");
        }

        if (destination.getStatus() == AccountStatus.BLOCKED) {
            throw new AccountBlockedException("Destination account " + destinationId + " is blocked"); // FIXED: sourceId -> destinationId
        }

        if (source.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient balance for account " + sourceId +
                            ". Available: " + source.getBalance() + ", Requested: " + amount
            );
        }

        source.setBalance(source.getBalance().subtract(amount));
        destination.setBalance(destination.getBalance().add(amount));

        // save both source and destination
        accountRepository.save(source);
        accountRepository.save(destination);

        // write audit log
        Transaction tx = new Transaction();
        tx.setType(TransactionType.TRANSFER);
        tx.setAmount(amount);
        tx.setStatus(TransactionStatus.SUCCESS);
        tx.setSourceAccountId(sourceId);
        tx.setDestinationAccountId(destinationId);
        transactionRepository.save(tx);



        // NEW: Publish the event. It fires ONLY after this method's
        // transaction commits. The listener runs on a separate thread.

        eventPublisher.publishEvent(new OperationCompletedEvent(
                TransactionType.TRANSFER,
                sourceId,
                destinationId,
                amount,
                LocalDateTime.now()
        ));
    }

    private void validateOwnership (Account source, Long sourceId, String currentUsername, boolean isAdmin) {
        if (isAdmin) {
            return;
        }
        Long currentUserId = userDetailsService.getOwnerIdForUsername(currentUsername);
        if (currentUserId == null || !source.getOwnerId().equals(currentUserId)) {
            throw new UnauthorizedAccessException("You do not own account " + sourceId);
        }
    }
}