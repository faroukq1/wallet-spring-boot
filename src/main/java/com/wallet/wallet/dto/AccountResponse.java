package com.wallet.wallet.dto;

import java.math.BigDecimal;

public record AccountResponse(
        Long id,
        BigDecimal balance,
        String status
) {
}
