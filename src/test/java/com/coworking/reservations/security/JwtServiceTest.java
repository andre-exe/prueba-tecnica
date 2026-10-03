package com.coworking.reservations.security;

import com.coworking.reservations.config.properties.JwtProperties;
import com.coworking.reservations.domain.entity.User;
import com.coworking.reservations.domain.enums.Role;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final String ISSUER = "coworking-test";

    private final SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

    private JwtService serviceWith(Duration expiration) {
        JwtProperties properties = new JwtProperties(SECRET, ISSUER, expiration);
        return new JwtService(new NimbusJwtEncoder(new ImmutableSecret<>(key)), properties);
    }

    private NimbusJwtDecoder decoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        return decoder;
    }

    private User userWithId(UUID id, Role role) {
        User user = new User("ana@test.com", "hash", "Ana", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void tokenCarriesExpectedClaims() {
        UUID id = UUID.randomUUID();
        String token = serviceWith(Duration.ofHours(1)).generateToken(userWithId(id, Role.ADMIN));

        Jwt jwt = decoder().decode(token);

        assertThat(jwt.getSubject()).isEqualTo(id.toString());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(ISSUER);
        assertThat(jwt.getClaimAsString("email")).isEqualTo("ana@test.com");
        assertThat(jwt.getClaimAsStringList("roles")).isEqualTo(List.of("ADMIN"));
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(jwt.getExpiresAt()).isAfter(jwt.getIssuedAt());
    }

    @Test
    void expiredTokenIsRejected() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(UUID.randomUUID().toString())
                .issuedAt(now.minus(Duration.ofHours(2)))
                .expiresAt(now.minus(Duration.ofHours(1)))
                .build();
        String token = new NimbusJwtEncoder(new ImmutableSecret<>(key))
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        assertThatThrownBy(() -> decoder().decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void expiresInIsReportedInSeconds() {
        assertThat(serviceWith(Duration.ofMinutes(30)).expiresInSeconds()).isEqualTo(1800);
    }
}
