package com.gaozhaoyang.agent.casework.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/dev")
public class DevTokenController {

    private final JwtEncoder jwtEncoder;
    private final String securityMode;

    public DevTokenController(JwtEncoder jwtEncoder, @Value("${app.security.mode:dev}") String securityMode) {
        this.jwtEncoder = jwtEncoder;
        this.securityMode = securityMode;
    }

    @PostMapping("/token")
    public TokenResponse token(@Valid @RequestBody TokenRequest request) {
        if (!"dev".equalsIgnoreCase(securityMode)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("agent-dev-assistant-local")
                .subject(request.userId())
                .issuedAt(now)
                .expiresAt(now.plus(8, ChronoUnit.HOURS))
                .claim("tenant_id", request.tenantId())
                .claim("roles", List.of(request.role().toUpperCase()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new TokenResponse(jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue());
    }

    public record TokenRequest(@NotBlank String userId, @NotBlank String tenantId, @NotBlank String role) {
    }

    public record TokenResponse(String accessToken) {
    }
}
