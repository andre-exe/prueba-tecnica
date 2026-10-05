package com.coworking.reservations.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    // un solo contenedor para toda la suite, se levanta la primera vez y se reutiliza
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    // el mismo mock del compose: lee los stubs de ./wiremock/mappings, asi los tests usan los tokens reales de la demo
    protected static final WireMockServer WIREMOCK = new WireMockServer(
            com.github.tomakehurst.wiremock.core.WireMockConfiguration.options()
                    .dynamicPort()
                    .usingFilesUnderDirectory("wiremock"));

    static {
        POSTGRES.start();
        WIREMOCK.start();
        warmUpWireMock();
    }

    // la primera llamada a wiremock siempre es mas lenta, se hace una vez antes de los tests para que no cuente como timeout
    private static void warmUpWireMock() {
        try {
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(
                            java.net.URI.create(WIREMOCK.baseUrl() + "/api/payments/validate"))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"paymentMethod\":\"tok_ok\"}"))
                    .build();
            java.net.http.HttpClient.newHttpClient().send(request, java.net.http.HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            // si falla el calentamiento los tests siguen, solo pueden salir mas lentos la primera vez
        }
    }

    @DynamicPropertySource
    static void paymentProperties(DynamicPropertyRegistry registry) {
        registry.add("app.payment.base-url", WIREMOCK::baseUrl);
    }

    protected static final String PASSWORD = "Secreta123";

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected ObjectMapper objectMapper;

    protected record TestUser(String email, String token) {
    }

    protected String adminToken() {
        return login("admin@test.local", "Admin1234!");
    }

    protected TestUser registerUser() {
        String email = "user-" + UUID.randomUUID() + "@test.com";
        ResponseEntity<String> response = send(HttpMethod.POST, "/api/v1/auth/register", null,
                Map.of("email", email, "password", PASSWORD, "fullName", "Usuario de pruebas"));
        Assertions.assertEquals(201, response.getStatusCode().value());
        return new TestUser(email, login(email, PASSWORD));
    }

    protected UUID createSpace(String adminToken, int capacity) {
        ResponseEntity<String> response = send(HttpMethod.POST, "/api/v1/spaces", adminToken, Map.of(
                "name", "Sala " + UUID.randomUUID(),
                "type", "MEETING_ROOM",
                "capacity", capacity,
                "location", "Piso de pruebas",
                "hourlyRate", 10));
        Assertions.assertEquals(201, response.getStatusCode().value());
        return UUID.fromString(field(response, "id"));
    }

    protected ResponseEntity<String> createReservation(String token, UUID spaceId, OffsetDateTime start,
                                                       OffsetDateTime end, int attendees) {
        return createReservation(token, spaceId, start, end, attendees, "tok_ok");
    }

    protected ResponseEntity<String> createReservation(String token, UUID spaceId, OffsetDateTime start,
                                                       OffsetDateTime end, int attendees, String paymentMethod) {
        return send(HttpMethod.POST, "/api/v1/reservations", token, Map.of(
                "spaceId", spaceId.toString(),
                "startTime", start.toString(),
                "endTime", end.toString(),
                "attendees", attendees,
                "paymentMethod", paymentMethod));
    }

    protected ResponseEntity<String> send(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    protected String field(ResponseEntity<String> response, String name) {
        try {
            return objectMapper.readTree(response.getBody()).get(name).asText();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo leer '" + name + "' de: " + response.getBody(), e);
        }
    }

    // una hora en punto dentro de unos dias, lejos de cualquier regla de tiempo
    protected OffsetDateTime futureHour(int daysAhead, int hour) {
        return OffsetDateTime.now(ZoneOffset.UTC).plusDays(daysAhead).truncatedTo(ChronoUnit.DAYS).plusHours(hour);
    }

    private String login(String email, String password) {
        ResponseEntity<String> response = send(HttpMethod.POST, "/api/v1/auth/login", null,
                Map.of("email", email, "password", password));
        Assertions.assertEquals(200, response.getStatusCode().value());
        return field(response, "accessToken");
    }
}
