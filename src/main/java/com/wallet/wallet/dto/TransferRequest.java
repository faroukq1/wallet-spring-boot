package com.wallet.wallet.dto;

import jakarta.validation.constraints.Digits;
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
    @Digits(integer = 17, fraction = 2, message = "Amount must have at most 2 decimal places")
    BigDecimal amount,
    @Size(max = 64, message = "Idempotency key must be at most 64 characters")
    String idempotencyKey
) { }
