package com.example.claims.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Local/demo identity configuration.
 *
 * <p>In a real deployment the users and token issuing live in an identity provider (e.g. Amazon Cognito,
 * Okta, Entra ID) and this service only <em>validates</em> tokens against the provider's JWKS. The
 * resource-server side of this config stays the same; the dev token endpoint and these users go away.
 */
@Validated
@ConfigurationProperties("app.security")
public record SecurityProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotEmpty List<User> users,
        List<String> corsAllowedOrigins) {

    public SecurityProperties {
        corsAllowedOrigins = corsAllowedOrigins == null ? List.of() : List.copyOf(corsAllowedOrigins);
    }

    /** @param secret HMAC-SHA256 key, at least 256 bits. Injected from a secret store outside local dev. */
    public record Jwt(@NotBlank @Size(min = 32) String secret, @NotBlank String issuer, @NotNull Duration ttl) {
    }

    public record User(@NotBlank String username, @NotBlank String password, @NotEmpty List<String> roles) {
    }
}
