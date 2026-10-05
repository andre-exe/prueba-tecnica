package com.coworking.reservations.integration;

import com.coworking.reservations.domain.entity.User;
import com.coworking.reservations.domain.enums.Role;
import com.coworking.reservations.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityIntegrationTest extends AbstractIntegrationTest {

    private static final String ISSUER = "coworking-reservations";

    @Autowired
    private JwtEncoder jwtEncoder;
    @Autowired
    private UserRepository userRepository;

    private int status(HttpMethod method, String path, String token) {
        return send(method, path, token, null).getStatusCode().value();
    }

    private String forgeToken(JwtEncoder encoder, String issuer, Instant issuedAt, Instant expiresAt, String role) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("email", "falso@test.com")
                .claim("roles", List.of(role))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    @Test
    void protectedEndpointsRejectRequestsWithoutAToken() {
        assertThat(status(HttpMethod.GET, "/api/v1/auth/me", null)).isEqualTo(401);
        assertThat(status(HttpMethod.GET, "/api/v1/spaces", null)).isEqualTo(401);
        assertThat(status(HttpMethod.GET, "/api/v1/reservations", null)).isEqualTo(401);
        assertThat(status(HttpMethod.GET, "/api/v1/reports/occupancy?from=2030-01-01T00:00:00Z&to=2030-01-02T00:00:00Z", null)).isEqualTo(401);
        assertThat(status(HttpMethod.GET, "/actuator/metrics", null)).isEqualTo(401);
    }

    @Test
    void publicEndpointsAreReachableWithoutAToken() {
        assertThat(status(HttpMethod.GET, "/actuator/health", null)).isEqualTo(200);
        assertThat(status(HttpMethod.GET, "/actuator/info", null)).isEqualTo(200);
        assertThat(status(HttpMethod.GET, "/v3/api-docs", null)).isEqualTo(200);
    }

    @Test
    void unauthorizedResponsesUseTheProblemFormat() {
        ResponseEntity<String> response = send(HttpMethod.GET, "/api/v1/auth/me", null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("application/problem+json");
        assertThat(field(response, "code")).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void garbageExpiredWrongSecretAndWrongIssuerTokensAreRejected() {
        Instant now = Instant.now();
        String expired = forgeToken(jwtEncoder, ISSUER, now.minus(Duration.ofHours(2)), now.minus(Duration.ofHours(1)), "ADMIN");
        JwtEncoder otherSecret = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec("otro-secreto-otro-secreto-otro-secreto".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        String wrongSecret = forgeToken(otherSecret, ISSUER, now, now.plus(Duration.ofHours(1)), "ADMIN");
        String wrongIssuer = forgeToken(jwtEncoder, "otro-emisor", now, now.plus(Duration.ofHours(1)), "ADMIN");

        assertThat(status(HttpMethod.GET, "/api/v1/auth/me", "abc.def.ghi")).isEqualTo(401);
        assertThat(status(HttpMethod.GET, "/api/v1/spaces", "no-es-un-token")).isEqualTo(401);
        assertThat(status(HttpMethod.GET, "/api/v1/spaces", expired)).isEqualTo(401);
        assertThat(status(HttpMethod.GET, "/api/v1/spaces", wrongSecret)).isEqualTo(401);
        assertThat(status(HttpMethod.GET, "/api/v1/spaces", wrongIssuer)).isEqualTo(401);
    }

    @Test
    void aUserCannotPromoteThemselvesByEditingTheirTokenPayload() {
        String userToken = registerUser().token();
        String[] parts = userToken.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String tampered = payload.replace("\"USER\"", "\"ADMIN\"");
        assertThat(tampered).isNotEqualTo(payload);
        String forged = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tampered.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

        assertThat(status(HttpMethod.GET, "/actuator/metrics", forged)).isEqualTo(401);
        assertThat(status(HttpMethod.GET, "/actuator/metrics", userToken)).isEqualTo(403);
    }

    @Test
    void regularUsersAreForbiddenFromAdminOperations() {
        String admin = adminToken();
        String user = registerUser().token();
        UUID spaceId = createSpace(admin, 10);
        Map<String, Object> body = Map.of("name", "Intruso " + UUID.randomUUID(), "type", "HOT_DESK", "capacity", 1,
                "location", "Piso 1", "hourlyRate", 5);

        assertThat(send(HttpMethod.POST, "/api/v1/spaces", user, body).getStatusCode().value()).isEqualTo(403);
        assertThat(send(HttpMethod.PUT, "/api/v1/spaces/" + spaceId, user, body).getStatusCode().value()).isEqualTo(403);
        assertThat(status(HttpMethod.DELETE, "/api/v1/spaces/" + spaceId, user)).isEqualTo(403);
        assertThat(status(HttpMethod.GET, "/api/v1/reports/occupancy?from=2030-01-01T00:00:00Z&to=2030-01-02T00:00:00Z", user)).isEqualTo(403);
        assertThat(status(HttpMethod.GET, "/actuator/metrics", user)).isEqualTo(403);
        assertThat(status(HttpMethod.GET, "/actuator/circuitbreakers", user)).isEqualTo(403);
        // y el admin sí
        assertThat(status(HttpMethod.GET, "/actuator/metrics", admin)).isEqualTo(200);
        assertThat(send(HttpMethod.PUT, "/api/v1/spaces/" + spaceId, admin, Map.of("name", "Renombrada " + UUID.randomUUID(),
                "type", "HOT_DESK", "capacity", 3, "location", "Piso 1", "hourlyRate", 5)).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void aReservationOfAnotherUserLooksLikeItDoesNotExist() {
        String admin = adminToken();
        UUID spaceId = createSpace(admin, 10);
        TestUser owner = registerUser();
        TestUser stranger = registerUser();
        String id = field(createReservation(owner.token(), spaceId, futureHour(7, 9), futureHour(7, 10), 2), "id");
        ResponseEntity<String> missing = send(HttpMethod.GET, "/api/v1/reservations/" + UUID.randomUUID(), stranger.token(), null);

        ResponseEntity<String> asStranger = send(HttpMethod.GET, "/api/v1/reservations/" + id, stranger.token(), null);

        assertThat(asStranger.getStatusCode().value()).isEqualTo(404);
        // la respuesta es idéntica a la de una reserva que no existe, así no se revela que existe
        assertThat(field(asStranger, "code")).isEqualTo(field(missing, "code"));
        assertThat(status(HttpMethod.GET, "/api/v1/reservations/" + id, owner.token())).isEqualTo(200);
        assertThat(status(HttpMethod.GET, "/api/v1/reservations/" + id, admin)).isEqualTo(200);
        assertThat(status(HttpMethod.POST, "/api/v1/reservations/" + id + "/cancel", stranger.token())).isEqualTo(404);
        assertThat(status(HttpMethod.POST, "/api/v1/reservations/" + id + "/confirm", stranger.token())).isEqualTo(404);
    }

    @Test
    void listsAreScopedToTheOwnerAndTheAdminSeesEverything() throws Exception {
        String admin = adminToken();
        UUID spaceId = createSpace(admin, 10);
        TestUser ana = registerUser();
        TestUser luis = registerUser();
        createReservation(ana.token(), spaceId, futureHour(8, 9), futureHour(8, 10), 2);
        createReservation(luis.token(), spaceId, futureHour(8, 11), futureHour(8, 12), 2);

        assertThat(emailsIn(send(HttpMethod.GET, "/api/v1/reservations?spaceId=" + spaceId, ana.token(), null)))
                .containsExactly(ana.email());
        assertThat(emailsIn(send(HttpMethod.GET, "/api/v1/reservations?spaceId=" + spaceId, luis.token(), null)))
                .containsExactly(luis.email());
        assertThat(emailsIn(send(HttpMethod.GET, "/api/v1/reservations?spaceId=" + spaceId, admin, null)))
                .containsExactlyInAnyOrder(ana.email(), luis.email());

        // un usuario normal no puede ver las de otro pidiendo su userId
        String luisId = field(send(HttpMethod.GET, "/api/v1/auth/me", luis.token(), null), "id");
        assertThat(emailsIn(send(HttpMethod.GET, "/api/v1/reservations?spaceId=" + spaceId + "&userId=" + luisId, ana.token(), null)))
                .containsExactly(ana.email());
        assertThat(emailsIn(send(HttpMethod.GET, "/api/v1/reservations?spaceId=" + spaceId + "&userId=" + luisId, admin, null)))
                .containsExactly(luis.email());
    }

    @Test
    void registrationAlwaysCreatesARegularUserEvenIfTheBodyAsksForAdmin() {
        String email = "intento-" + UUID.randomUUID() + "@test.com";

        ResponseEntity<String> response = send(HttpMethod.POST, "/api/v1/auth/register", null,
                Map.of("email", email, "password", PASSWORD, "fullName", "Intento", "role", "ADMIN"));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(field(response, "role")).isEqualTo("USER");
        assertThat(userRepository.findByEmail(email).orElseThrow().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void passwordsAreStoredHashedAndNeverReturned() {
        TestUser user = registerUser();

        User stored = userRepository.findByEmail(user.email()).orElseThrow();
        ResponseEntity<String> me = send(HttpMethod.GET, "/api/v1/auth/me", user.token(), null);

        assertThat(stored.getPasswordHash()).startsWith("$2").doesNotContain(PASSWORD);
        assertThat(me.getBody()).doesNotContain("password").doesNotContain("$2");
    }

    @Test
    void emailsAreUniqueIgnoringCase() {
        TestUser existing = registerUser();

        ResponseEntity<String> duplicate = send(HttpMethod.POST, "/api/v1/auth/register", null,
                Map.of("email", existing.email().toUpperCase(), "password", PASSWORD, "fullName", "Otra persona"));

        assertThat(duplicate.getStatusCode().value()).isEqualTo(409);
        assertThat(field(duplicate, "code")).isEqualTo("EMAIL_ALREADY_EXISTS");
    }

    @Test
    void loginGivesTheSameAnswerForAWrongPasswordAndAnUnknownEmail() {
        TestUser user = registerUser();

        ResponseEntity<String> wrongPassword = send(HttpMethod.POST, "/api/v1/auth/login", null,
                Map.of("email", user.email(), "password", "clave-incorrecta"));
        ResponseEntity<String> unknownEmail = send(HttpMethod.POST, "/api/v1/auth/login", null,
                Map.of("email", "nadie-" + UUID.randomUUID() + "@test.com", "password", PASSWORD));

        assertThat(wrongPassword.getStatusCode().value()).isEqualTo(401);
        assertThat(unknownEmail.getStatusCode().value()).isEqualTo(401);
        assertThat(field(wrongPassword, "detail")).isEqualTo(field(unknownEmail, "detail"));
    }

    @Test
    void theLoginTokenCarriesTheExpectedClaims() throws Exception {
        TestUser user = registerUser();
        String[] parts = user.token().split("\\.");

        JsonNode payload = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[1]));

        assertThat(payload.get("iss").asText()).isEqualTo(ISSUER);
        assertThat(payload.get("email").asText()).isEqualTo(user.email());
        assertThat(payload.get("roles").get(0).asText()).isEqualTo("USER");
        assertThat(payload.get("exp").asLong()).isGreaterThan(payload.get("iat").asLong());
    }

    private Set<String> emailsIn(ResponseEntity<String> page) throws Exception {
        Set<String> emails = new HashSet<>();
        for (JsonNode row : objectMapper.readTree(page.getBody()).get("content")) {
            emails.add(row.get("userEmail").asText());
        }
        return emails;
    }
}
