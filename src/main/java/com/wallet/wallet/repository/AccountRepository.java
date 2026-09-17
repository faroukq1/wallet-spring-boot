package com.wallet.wallet.repository;

import com.wallet.wallet.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    // CRITICAL FOR CONCURRENCY TEST: Pessimistic Write Lock
    // This translates to: SELECT * FROM accounts WHERE id = ? FOR UPDATE
    // It blocks other threads from reading/writing this row until the transaction commits.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findWithLockById(@Param("id") Long id);
}
