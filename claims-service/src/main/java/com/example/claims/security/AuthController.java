package com.example.claims.security;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.List;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev/demo login: exchanges username + password for a JWT. In production this is the identity
 * provider's job (OIDC authorization code flow); the rest of the API is unchanged.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Demo login (replaced by an identity provider in production)")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokens;

    public AuthController(AuthenticationManager authenticationManager, TokenService tokens) {
        this.authenticationManager = authenticationManager;
        this.tokens = tokens;
    }

    public record TokenRequest(@NotBlank String username, @NotBlank String password) {
        @Override
        public String toString() {
            return "TokenRequest[username=" + username + ", password=***]"; // never log the password
        }
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn, String username,
                                List<String> roles) {
    }

    @PostMapping("/token")
    @SecurityRequirements // no bearer token needed to call this
    @Operation(summary = "Exchange demo credentials for a bearer token")
    public TokenResponse token(@Valid @RequestBody TokenRequest request) {
        Authentication auth = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        TokenService.IssuedToken token = tokens.issue(auth);
        long expiresIn = Duration.between(token.issuedAt(), token.expiresAt()).toSeconds();
        return new TokenResponse(token.value(), "Bearer", expiresIn, token.username(), token.roles());
    }
}
