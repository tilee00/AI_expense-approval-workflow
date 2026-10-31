package com.example.expense;

import com.example.expense.user.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

class TestTokens {

    private final JwtEncoder encoder;

    TestTokens(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    String forUser(User user) {
        return token(user, Instant.now().plus(1, ChronoUnit.HOURS), true);
    }

    String expired(User user) {
        return token(user, Instant.now().minus(2, ChronoUnit.HOURS), true);
    }

    String withoutRoles(User user) {
        return token(user, Instant.now().plus(1, ChronoUnit.HOURS), false);
    }

    private String token(User user, Instant expiresAt, boolean withRoles) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(user.getUsername())
                .issuedAt(expiresAt.minus(1, ChronoUnit.HOURS))
                .expiresAt(expiresAt);
        if (withRoles) {
            List<String> roles = user.getRoles().stream()
                    .map(role -> "ROLE_" + role.name())
                    .toList();
            claims.claim("roles", roles);
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }
}
