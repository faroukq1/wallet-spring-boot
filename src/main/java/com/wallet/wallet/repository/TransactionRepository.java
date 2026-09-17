package com.wallet.wallet.repository;

import com.wallet.wallet.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // Derived query: SELECT * FROM transactions WHERE source_account_id = ? OR destination_account_id = ?
    // This is your "history" endpoint's data source.
    // It fetches everything an account sent (source) or received (destination).
    List<Transaction> findBySourceAccountIdOrDestinationAccountId(Long sourceId, Long destinationId);
}