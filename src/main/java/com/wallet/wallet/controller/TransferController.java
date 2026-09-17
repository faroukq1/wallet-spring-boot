package com.wallet.wallet.controller;


import com.wallet.wallet.dto.TransferRequest;
import com.wallet.wallet.dto.TransferResponse;
import com.wallet.wallet.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transfers")
@Tag(name = "Transfers", description = "Transfer money between accounts")
public class TransferController {
    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @Operation(
            summary = "Transfer money between accounts",
            description = "Moves an amount from the source account to the destination account atomically. "
                    + "Concurrency-safe. Optionally idempotent: pass an idempotencyKey to make the transfer safe to retry."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transfer completed",
                    content = @Content(schema = @Schema(implementation = TransferResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid amount, insufficient balance or invalid operation"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Account blocked or inactive"),
            @ApiResponse(responseCode = "404", description = "Account not found"),
            @ApiResponse(responseCode = "409", description = "Duplicate transaction (idempotency key already used)")
    })
    public ResponseEntity<TransferResponse> transfer (
            @Valid @RequestBody TransferRequest request
            ) {
        transferService.transfer(
                request.sourceAccountId(),
                request.destinationAccountId(),
                request.amount(),
                currentUsername(),
                isCurrentUserAdmin(),
                request.idempotencyKey()
        );

        return ResponseEntity.ok(new TransferResponse("Transfer completed successfully"));
    }

    private static String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private static boolean isCurrentUserAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

}
