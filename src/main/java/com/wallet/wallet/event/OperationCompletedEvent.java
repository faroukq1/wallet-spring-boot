package com.wallet.wallet.event;

import com.wallet.wallet.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Plain data object representing a successful operation.
 * Published AFTER the DB transaction commits.
 */
public record OperationCompletedEvent(
        TransactionType operationType,
        Long sourceAccountId,
        Long destinationAccountId,
        BigDecimal amount,
        LocalDateTime completedAt
) {}