package com.wallet.wallet.dto;

import com.wallet.wallet.entity.Transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        Long id,
        String type,
        BigDecimal amount,
        String status,
        Long sourceAccountId,
        Long destinationAccountId,
        LocalDateTime timestamp
) {
    public static TransactionResponse from(Transaction tx) {
        return new TransactionResponse(
                tx.getId(),
                tx.getType().name(),
                tx.getAmount(),
                tx.getStatus().name(),
                tx.getSourceAccountId(),
                tx.getDestinationAccountId(),
                tx.getTimestamp()
        );
    }
}
