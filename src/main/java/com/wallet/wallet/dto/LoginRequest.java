package com.wallet.wallet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credentials used to obtain a JWT")
public record LoginRequest(
        @Schema(description = "Demo USER login", example = "alice")
        @NotBlank(message = "Username is required")
        String username,
        @Schema(description = "Demo USER password", example = "password")
        @NotBlank(message = "Password is required")
        String password
) {}
