package com.example.claims.security;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues short-lived access tokens for the dev login endpoint. */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final SecurityProperties props;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, SecurityProperties props, Clock clock) {
        this.encoder = encoder;
        this.props = props;
        this.clock = clock;
    }

    public IssuedToken issue(Authentication authentication) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(props.jwt().ttl());
        // Only ROLE_* authorities become roles. Spring Security 7 also adds factor authorities such as
        // FACTOR_PASSWORD (how the user authenticated); those must not leak into the token as roles.
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .sorted()
                .toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.jwt().issuer())
                .subject(authentication.getName())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(SecurityConfig.ROLES_CLAIM, roles)
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new IssuedToken(token, authentication.getName(), roles, now, expiresAt);
    }

    public record IssuedToken(String value, String username, List<String> roles, Instant issuedAt, Instant expiresAt) {
    }
}
