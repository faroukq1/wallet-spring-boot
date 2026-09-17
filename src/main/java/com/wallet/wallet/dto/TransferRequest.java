package com.wallet.wallet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TransferRequest(
    @NotNull(message = "Source account ID is required")
    Long sourceAccountId,
    @NotNull(message = "Destination account ID is required")
    Long destinationAccountId,
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    BigDecimal amount,
    @Size(max = 64, message = "Idempotency key must be at most 64 characters")
    String idempotencyKey
) { }
