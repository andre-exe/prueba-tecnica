package com.coworking.reservations.integration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.coworking.reservations.listener.NotificationListener;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class PaymentConfirmationIntegrationTest extends AbstractIntegrationTest {

    private static final String VALIDATE_PATH = "/api/payments/validate";

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreaker circuitBreaker;
    private String adminToken;
    private TestUser user;

    @BeforeEach
    void setUp() {
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("paymentService");
        circuitBreaker.reset();
        adminToken = adminToken();
        user = registerUser();
    }

    private String newReservation(String paymentMethod) {
        UUID spaceId = createSpace(adminToken, 10);
        ResponseEntity<String> response = createReservation(user.token(), spaceId, futureHour(3, 10),
                futureHour(3, 11), 2, paymentMethod);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        return field(response, "id");
    }

    private ResponseEntity<String> confirm(String reservationId) {
        return send(HttpMethod.POST, "/api/v1/reservations/" + reservationId + "/confirm", user.token(), null);
    }

    private String statusOf(String reservationId) {
        return field(send(HttpMethod.GET, "/api/v1/reservations/" + reservationId, user.token(), null), "status");
    }

    private String reservationStatusIn(ResponseEntity<String> confirmation) {
        try {
            return objectMapper.readTree(confirmation.getBody()).get("reservation").get("status").asText();
        } catch (Exception e) {
            throw new IllegalStateException(confirmation.getBody(), e);
        }
    }

    private long paymentCalls() {
        return WIREMOCK.countRequestsMatching(postRequestedFor(urlPathEqualTo(VALIDATE_PATH)).build()).getCount();
    }

    @Test
    void approvedPaymentConfirmsTheReservation() {
        String id = newReservation("tok_ok");

        ResponseEntity<String> response = confirm(id);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(reservationStatusIn(response)).isEqualTo("CONFIRMED");
        assertThat(statusOf(id)).isEqualTo("CONFIRMED");
    }

    @Test
    void declinedPaymentReturns402AndKeepsTheReservationPending() {
        String id = newReservation("tok_declined");

        ResponseEntity<String> response = confirm(id);

        assertThat(response.getStatusCode().value()).isEqualTo(402);
        assertThat(field(response, "code")).isEqualTo("PAYMENT_DECLINED");
        assertThat(statusOf(id)).isEqualTo("PENDING");
        // un rechazo es normal del negocio, no debe contar como falla del proveedor
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void unknownPaymentMethodIsDeclined() {
        ResponseEntity<String> response = confirm(newReservation("tok_inventado"));

        assertThat(response.getStatusCode().value()).isEqualTo(402);
    }

    @Test
    void providerErrorLeavesTheReservationPendingPaymentWith202() {
        String id = newReservation("tok_error");

        ResponseEntity<String> response = confirm(id);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(reservationStatusIn(response)).isEqualTo("PENDING_PAYMENT");
        assertThat(field(response, "message")).contains("reintentar");
        assertThat(statusOf(id)).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void slowProviderTimesOutAndFallsBackToPendingPayment() {
        String id = newReservation("tok_slow");

        ResponseEntity<String> response = confirm(id);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(statusOf(id)).isEqualTo("PENDING_PAYMENT");
        // un timeout cuenta como falla del proveedor, igual que un 500
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @Test
    void confirmingTwiceIsAConflictAndDoesNotChargeAgain() {
        String id = newReservation("tok_ok");
        confirm(id);
        long callsAfterFirst = paymentCalls();

        ResponseEntity<String> second = confirm(id);

        assertThat(second.getStatusCode().value()).isEqualTo(409);
        assertThat(paymentCalls()).isEqualTo(callsAfterFirst);
    }

    @Test
    void anotherUserCannotConfirmSomeoneElsesReservation() {
        String id = newReservation("tok_ok");
        TestUser stranger = registerUser();

        ResponseEntity<String> response = send(HttpMethod.POST, "/api/v1/reservations/" + id + "/confirm", stranger.token(), null);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void cancelledReservationCannotBeConfirmed() {
        String id = newReservation("tok_ok");
        send(HttpMethod.POST, "/api/v1/reservations/" + id + "/cancel", user.token(), null);

        assertThat(confirm(id).getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void circuitOpensAfterRepeatedFailuresAndRecoversWhenTheProviderComesBack() {
        List<String> failing = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            failing.add(newReservation("tok_error"));
        }

        // tres fallas seguidas llegan al minimo de llamadas y el circuito se abre
        failing.forEach(id -> assertThat(confirm(id).getStatusCode().value()).isEqualTo(202));
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // con el circuito abierto ni siquiera se llama al proveedor, aunque el token sea bueno
        long callsBefore = paymentCalls();
        String healthyToken = newReservation("tok_ok");
        ResponseEntity<String> whileOpen = confirm(healthyToken);
        assertThat(whileOpen.getStatusCode().value()).isEqualTo(202);
        assertThat(reservationStatusIn(whileOpen)).isEqualTo("PENDING_PAYMENT");
        assertThat(paymentCalls()).isEqualTo(callsBefore);

        // el proveedor se recupera: el mismo token que fallaba ahora aprueba
        StubMapping recovered = WIREMOCK.stubFor(post(urlPathEqualTo(VALIDATE_PATH))
                .withRequestBody(equalToJson("{\"paymentMethod\":\"tok_error\"}", true, true))
                .atPriority(1)
                .willReturn(okJson("{\"approved\":true,\"transactionId\":\"txn-recuperado\",\"reason\":null}")));
        try {
            await().atMost(Duration.ofSeconds(10)).until(() -> circuitBreaker.getState() == CircuitBreaker.State.HALF_OPEN);

            ResponseEntity<String> retry = confirm(failing.get(0));
            assertThat(retry.getStatusCode().value()).isEqualTo(200);
            assertThat(reservationStatusIn(retry)).isEqualTo("CONFIRMED");
            await().atMost(Duration.ofSeconds(5)).until(() -> circuitBreaker.getState() == CircuitBreaker.State.CLOSED);

            // con el circuito cerrado la reserva que quedo pendiente de pago tambien se puede confirmar
            assertThat(confirm(healthyToken).getStatusCode().value()).isEqualTo(200);
        } finally {
            WIREMOCK.removeStub(recovered);
        }
    }

    @Test
    void notificationIsSentAfterTheResponseAndOnAnotherThread() {
        CapturingAppender appender = new CapturingAppender();
        appender.start();
        Logger logger = (Logger) LoggerFactory.getLogger(NotificationListener.class);
        logger.addAppender(appender);
        try {
            String id = newReservation("tok_ok");

            long startedAt = System.nanoTime();
            ResponseEntity<String> response = confirm(id);
            long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

            // el correo simulado tarda 3 segundos: la respuesta tiene que salir antes y sin el correo enviado
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(elapsedMillis).isLessThan(3000);
            assertThat(emailsFor(appender, id)).isEmpty();

            await().atMost(Duration.ofSeconds(15)).until(() -> !emailsFor(appender, id).isEmpty());
            ILoggingEvent email = emailsFor(appender, id).get(0);
            assertThat(email.getThreadName()).startsWith("notification-");
            assertThat(email.getFormattedMessage()).contains(user.email()).contains("Reserva confirmada");
        } finally {
            logger.detachAppender(appender);
        }
    }

    private List<ILoggingEvent> emailsFor(CapturingAppender appender, String reservationId) {
        return appender.events.stream().filter(e -> e.getFormattedMessage().contains(reservationId)).toList();
    }

    private static class CapturingAppender extends AppenderBase<ILoggingEvent> {
        final List<ILoggingEvent> events = new CopyOnWriteArrayList<>();

        @Override
        protected void append(ILoggingEvent event) {
            events.add(event);
        }
    }
}
