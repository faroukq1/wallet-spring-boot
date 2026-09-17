package com.wallet.wallet.controller;

import com.wallet.wallet.dto.LoginRequest;
import com.wallet.wallet.dto.LoginResponse;
import com.wallet.wallet.security.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Login and JWT token issuance. Demo users: alice / password (USER), admin / admin (ADMIN). "
        + "Execute login, copy the token, then click Authorize and paste it.")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;

    public AuthController(AuthenticationManager authenticationManager,
                          UserDetailsService userDetailsService,
                          JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    @Operation(
            summary = "Log in",
            description = "Verifies username/password and returns a JWT. The request body is pre-filled with the demo user "
                    + "(alice / password) so you can Execute right away. Copy the token and paste it into the Authorize dialog."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful, JWT issued",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Invalid username or password")
    })
    public ResponseEntity<LoginResponse> login(
            @Valid
            @org.springframework.web.bind.annotation.RequestBody
            @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    schema = @Schema(implementation = LoginRequest.class),
                    examples = @ExampleObject(name = "alice (demo user)",
                            value = "{\"username\": \"alice\", \"password\": \"password\"}")
            ))
            LoginRequest request) {
        // 1. Ask Spring Security to verify the username/password.
        //    If wrong, it throws BadCredentialsException -> our GlobalExceptionHandler
        //    will translate this to 401.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        // 2. Load the full user (with roles).
        UserDetails user = userDetailsService.loadUserByUsername(request.username());

        // 3. Generate a JWT.
        String token = jwtService.generateToken(user);

        String role = user.getAuthorities().iterator().next().getAuthority();
        return ResponseEntity.ok(new LoginResponse(token, user.getUsername(), role));
    }
}
