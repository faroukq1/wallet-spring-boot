package com.wallet.wallet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TransferRequest(
    @NotNull(message = "Source account ID is required")
    Long sourceAccountId,
    @NotNull(message = "Destination account ID is required")
    Long destinationAccountId,
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    BigDecimal amount
) { }
