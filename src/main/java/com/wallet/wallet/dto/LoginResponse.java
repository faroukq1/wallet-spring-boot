package com.wallet.wallet.dto;

public record LoginResponse(
        String token,
        String username,
        String role
) {}
