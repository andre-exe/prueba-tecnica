package com.coworking.reservations.integration;

import com.coworking.reservations.domain.entity.Reservation;
import com.coworking.reservations.domain.entity.Space;
import com.coworking.reservations.domain.entity.User;
import com.coworking.reservations.domain.enums.ReservationStatus;
import com.coworking.reservations.repository.ReservationRepository;
import com.coworking.reservations.repository.SpaceRepository;
import com.coworking.reservations.repository.UserRepository;
import com.coworking.reservations.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationLifecycleIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private SpaceRepository spaceRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ReservationService reservationService;

    private ResponseEntity<String> post(String path, String token) {
        return send(HttpMethod.POST, path, token, null);
    }

    private String create(String token, UUID spaceId, OffsetDateTime start) {
        ResponseEntity<String> response = createReservation(token, spaceId, start, start.plusHours(1), 2);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        return field(response, "id");
    }

    @Test
    void cancellingFreesTheSlotForSomeoneElse() {
        UUID spaceId = createSpace(adminToken(), 10);
        TestUser owner = registerUser();
        TestUser other = registerUser();
        OffsetDateTime start = futureHour(3, 10);
        String id = create(owner.token(), spaceId, start);

        ResponseEntity<String> cancelled = post("/api/v1/reservations/" + id + "/cancel", owner.token());
        assertThat(cancelled.getStatusCode().value()).isEqualTo(200);
        assertThat(field(cancelled, "status")).isEqualTo("CANCELLED");

        ResponseEntity<String> again = createReservation(other.token(), spaceId, start, start.plusHours(1), 2);
        assertThat(again.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void anotherUserCannotCancelButAdminCan() {
        UUID spaceId = createSpace(adminToken(), 10);
        TestUser owner = registerUser();
        TestUser stranger = registerUser();
        String id = create(owner.token(), spaceId, futureHour(3, 12));

        assertThat(post("/api/v1/reservations/" + id + "/cancel", stranger.token()).getStatusCode().value()).isEqualTo(404);
        assertThat(post("/api/v1/reservations/" + id + "/cancel", adminToken()).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void cancellingTwiceIsAConflict() {
        UUID spaceId = createSpace(adminToken(), 10);
        TestUser owner = registerUser();
        String id = create(owner.token(), spaceId, futureHour(3, 14));

        post("/api/v1/reservations/" + id + "/cancel", owner.token());
        ResponseEntity<String> second = post("/api/v1/reservations/" + id + "/cancel", owner.token());

        assertThat(second.getStatusCode().value()).isEqualTo(409);
        assertThat(field(second, "code")).isEqualTo("INVALID_RESERVATION_STATE");
    }

    @Test
    void onlyAdminCanCompleteAndNotBeforeItEnds() {
        UUID spaceId = createSpace(adminToken(), 10);
        TestUser owner = registerUser();
        String id = create(owner.token(), spaceId, futureHour(3, 16));

        assertThat(post("/api/v1/reservations/" + id + "/complete", owner.token()).getStatusCode().value()).isEqualTo(403);
        assertThat(post("/api/v1/reservations/" + id + "/complete", adminToken()).getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void adminCompletesAFinishedConfirmedReservation() {
        UUID id = persistFinishedConfirmedReservation();

        ResponseEntity<String> response = post("/api/v1/reservations/" + id + "/complete", adminToken());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(field(response, "status")).isEqualTo("COMPLETED");
    }

    @Test
    void completionJobMarksFinishedReservationsAsCompleted() {
        UUID finished = persistFinishedConfirmedReservation();
        UUID stillPending = persistPendingFutureReservation();

        reservationService.completeExpired();

        assertThat(reservationRepository.findById(finished).orElseThrow().getStatus()).isEqualTo(ReservationStatus.COMPLETED);
        assertThat(reservationRepository.findById(stillPending).orElseThrow().getStatus()).isEqualTo(ReservationStatus.PENDING);
    }

    private UUID persistFinishedConfirmedReservation() {
        OffsetDateTime end = OffsetDateTime.now(ZoneOffset.UTC).minusHours(2);
        Reservation reservation = newReservation(end.minusHours(1), end);
        reservation.confirm();
        return reservationRepository.saveAndFlush(reservation).getId();
    }

    private UUID persistPendingFutureReservation() {
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(2);
        return reservationRepository.saveAndFlush(newReservation(start, start.plusHours(1))).getId();
    }

    private Reservation newReservation(OffsetDateTime start, OffsetDateTime end) {
        User user = userRepository.findByEmail(registerUser().email()).orElseThrow();
        Space space = spaceRepository.findById(createSpace(adminToken(), 10)).orElseThrow();
        return new Reservation(user, space, start, end, 2, BigDecimal.TEN, "tok_ok");
    }
}
