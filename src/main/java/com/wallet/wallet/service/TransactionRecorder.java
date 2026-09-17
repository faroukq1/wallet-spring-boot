package com.wallet.wallet.service;

import com.wallet.wallet.entity.Transaction;
import com.wallet.wallet.entity.TransactionStatus;
import com.wallet.wallet.entity.TransactionType;
import com.wallet.wallet.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Records a FAILED transaction trace when an operation is rejected by a
 * business rule. Runs in a REQUIRES_NEW transaction so the trace survives
 * the rollback of the main (failing) transaction.
 */
@Service
public class TransactionRecorder {

    private final TransactionRepository transactionRepository;

    public TransactionRecorder(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailed(TransactionType type, BigDecimal amount,
                             Long sourceAccountId, Long destinationAccountId) {
        Transaction tx = new Transaction();
        tx.setType(type);
        tx.setAmount(amount);
        tx.setStatus(TransactionStatus.FAILED);
        tx.setSourceAccountId(sourceAccountId);
        tx.setDestinationAccountId(destinationAccountId);
        transactionRepository.save(tx);
    }
}
