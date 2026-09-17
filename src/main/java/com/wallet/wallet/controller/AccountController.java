package com.wallet.wallet.controller;

import com.wallet.wallet.dto.AccountResponse;
import com.wallet.wallet.dto.DepositRequest;
import com.wallet.wallet.dto.WithdrawRequest;
import com.wallet.wallet.entity.Transaction;
import com.wallet.wallet.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/accounts")
@Tag(name = "Accounts", description = "Deposit, withdraw, balance and transaction history")
public class AccountController {
    private final AccountService accountService;


    public AccountController (AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/{id}/deposit")
    @Operation(summary = "Deposit money into an account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Deposit successful",
                    content = @Content(schema = @Schema(implementation = AccountResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid amount or operation"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Account blocked or inactive"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public ResponseEntity<AccountResponse> deposit (
            @Parameter(description = "ID of the account to deposit into", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody DepositRequest request
            ) {
        AccountResponse response = accountService.deposit(id, request.amount(), currentUsername(), isCurrentUserAdmin());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/withdraw")
    @Operation(summary = "Withdraw money from an account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Withdrawal successful",
                    content = @Content(schema = @Schema(implementation = AccountResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid amount, insufficient balance or invalid operation"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Account blocked or inactive"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public ResponseEntity<AccountResponse> withdraw (
            @Parameter(description = "ID of the account to withdraw from", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody WithdrawRequest request
            ) {
        AccountResponse response = accountService.withdraw(id, request.amount(), currentUsername(), isCurrentUserAdmin());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get account balance and status")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Account found",
                    content = @Content(schema = @Schema(implementation = AccountResponse.class))),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Account blocked or inactive"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public ResponseEntity<AccountResponse> getAccount (
            @Parameter(description = "ID of the account to fetch", example = "1")
            @PathVariable Long id) {
        return ResponseEntity.ok(accountService.getAccount(id, currentUsername(), isCurrentUserAdmin()));
    }


    @GetMapping("/{id}/transactions")
    @Operation(summary = "Get an account's transaction history")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transaction list",
                    content = @Content(schema = @Schema(implementation = Transaction.class))),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public ResponseEntity<List<Transaction>> getHistory(
            @Parameter(description = "ID of the account whose history to fetch", example = "1")
            @PathVariable Long id) {
        return ResponseEntity.ok(accountService.getHistory(id, currentUsername(), isCurrentUserAdmin()));
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
