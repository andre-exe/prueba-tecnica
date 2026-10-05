package com.coworking.reservations.integration;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ActuatorAndDocsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreaker circuitBreaker;
    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("paymentService");
        circuitBreaker.reset();
        adminToken = adminToken();
        userToken = registerUser().token();
    }

    @AfterEach
    void tearDown() {
        circuitBreaker.reset();
    }

    private ResponseEntity<String> get(String path, String token) {
        return send(HttpMethod.GET, path, token, null);
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    @Test
    void healthIsPublicButHidesDetailsFromAnonymousUsers() throws Exception {
        ResponseEntity<String> response = get("/actuator/health", null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(json(response).get("status").asText()).isEqualTo("UP");
        assertThat(json(response).has("components")).isFalse();
    }

    @Test
    void healthShowsTheCircuitBreakerToAdmins() throws Exception {
        JsonNode body = json(get("/actuator/health", adminToken));

        assertThat(body.get("components").has("circuitBreakers")).isTrue();
        assertThat(body.get("components").get("circuitBreakers").get("details").has("paymentService")).isTrue();
    }

    @Test
    void healthStaysUpEvenWhenTheCircuitIsOpen() throws Exception {
        // si el pago esta caido la app sigue sana: la reserva queda pendiente de pago, no se rechaza la peticion
        circuitBreaker.transitionToOpenState();

        ResponseEntity<String> response = get("/actuator/health", adminToken);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(json(response).get("status").asText()).isEqualTo("UP");
    }

    @Test
    void infoIsPublicAndShowsAppAndBuildData() throws Exception {
        ResponseEntity<String> response = get("/actuator/info", null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(json(response).get("app").get("name").asText()).isEqualTo("coworking-reservations");
        assertThat(json(response).has("build")).isTrue();
    }

    @Test
    void metricsRequireAdmin() throws Exception {
        assertThat(get("/actuator/metrics", null).getStatusCode().value()).isEqualTo(401);
        assertThat(get("/actuator/metrics", userToken).getStatusCode().value()).isEqualTo(403);

        ResponseEntity<String> asAdmin = get("/actuator/metrics", adminToken);
        assertThat(asAdmin.getStatusCode().value()).isEqualTo(200);
        assertThat(json(asAdmin).get("names").toString()).contains("jvm.memory.used");
    }

    @Test
    void circuitBreakerStateIsVisibleInActuator() throws Exception {
        assertThat(get("/actuator/circuitbreakers", userToken).getStatusCode().value()).isEqualTo(403);

        JsonNode closed = json(get("/actuator/circuitbreakers", adminToken));
        assertThat(closed.get("circuitBreakers").get("paymentService").get("state").asText()).isEqualTo("CLOSED");

        circuitBreaker.transitionToOpenState();

        JsonNode open = json(get("/actuator/circuitbreakers", adminToken));
        assertThat(open.get("circuitBreakers").get("paymentService").get("state").asText()).isEqualTo("OPEN");
        assertThat(get("/actuator/circuitbreakerevents", adminToken).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void openApiDocumentDescribesTheApiWithBearerSecurity() throws Exception {
        ResponseEntity<String> response = get("/v3/api-docs", null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode api = json(response);
        assertThat(api.get("paths").has("/api/v1/reservations/{id}/confirm")).isTrue();
        assertThat(api.get("paths").has("/api/v1/spaces")).isTrue();
        assertThat(api.get("paths").has("/api/v1/reports/occupancy")).isTrue();
        assertThat(api.get("components").get("securitySchemes").get("bearerAuth").get("scheme").asText()).isEqualTo("bearer");
        assertThat(api.get("paths").get("/api/v1/auth/login").get("post").get("security").size()).isZero();
        assertThat(api.get("paths").get("/api/v1/reservations/{id}/confirm").get("post").get("responses").has("402")).isTrue();
    }

    @Test
    void swaggerUiIsServed() {
        assertThat(get("/swagger-ui/index.html", null).getStatusCode().value()).isEqualTo(200);
    }
}
