package com.gaozhaoyang.agent.casework.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class CaseSecurityConfiguration {

    @Bean
    SecurityFilterChain caseSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/", "/index.html", "/implementer-workbench.html",
                                "/case-ui.css", "/favicon.ico", "/api/dev/token").permitAll()
                        .requestMatchers("/api/cases/**").authenticated()
                        .requestMatchers("/api/workflows/**").hasRole("ADMIN")
                        .requestMatchers("/api/knowledge/**", "/api/tools/**", "/api/mcp/**",
                                "/api/system/**", "/api/observability/**")
                        .hasAnyRole("IMPLEMENTER", "ADMIN")
                        .anyRequest().permitAll())
                .oauth2ResourceServer(resource -> resource
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    JwtDecoder caseJwtDecoder(
            @Value("${app.security.mode:dev}") String mode,
            @Value("${app.security.issuer-uri:}") String issuerUri,
            SecretKey caseDevJwtSecret
    ) {
        if ("oidc".equalsIgnoreCase(mode)) {
            if (issuerUri == null || issuerUri.isBlank()) {
                throw new IllegalStateException("OIDC 模式必须配置 OIDC_ISSUER_URI");
            }
            return JwtDecoders.fromIssuerLocation(issuerUri);
        }
        return NimbusJwtDecoder.withSecretKey(caseDevJwtSecret).build();
    }

    @Bean
    NimbusJwtEncoder caseDevJwtEncoder(SecretKey caseDevJwtSecret) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(caseDevJwtSecret));
    }

    @Bean
    SecretKey caseDevJwtSecret(@Value("${app.security.dev-secret}") String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("CASE_AUTH_DEV_SECRET 至少需要 32 字节");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles == null) {
                return List.of();
            }
            return roles.stream()
                    .map(String::toUpperCase)
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .map(authority -> (org.springframework.security.core.GrantedAuthority) authority)
                    .toList();
        });
        return converter;
    }
}
