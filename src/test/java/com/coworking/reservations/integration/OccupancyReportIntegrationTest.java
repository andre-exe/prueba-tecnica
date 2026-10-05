package com.coworking.reservations.integration;

import com.coworking.reservations.domain.entity.Reservation;
import com.coworking.reservations.domain.entity.Space;
import com.coworking.reservations.domain.entity.User;
import com.coworking.reservations.repository.ReservationRepository;
import com.coworking.reservations.repository.SpaceRepository;
import com.coworking.reservations.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class OccupancyReportIntegrationTest extends AbstractIntegrationTest {

    @MockitoSpyBean
    private ReservationRepository reservationRepository;
    @Autowired
    private SpaceRepository spaceRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CacheManager cacheManager;

    private String adminToken;

    @BeforeEach
    void setUp() {
        cacheManager.getCache("occupancyReport").clear();
        adminToken = adminToken();
    }

    private OffsetDateTime midnight(int daysAgo) {
        return LocalDate.now(ZoneOffset.UTC).minusDays(daysAgo).atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    private ResponseEntity<String> report(String token, String from, String to) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        URI uri = URI.create(rest.getRootUri() + "/api/v1/reports/occupancy?from=" + from + "&to=" + to);
        return rest.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private JsonNode rowFor(ResponseEntity<String> response, UUID spaceId) throws Exception {
        for (JsonNode row : objectMapper.readTree(response.getBody())) {
            if (row.get("spaceId").asText().equals(spaceId.toString())) {
                return row;
            }
        }
        throw new AssertionError("el espacio " + spaceId + " no aparece en el reporte");
    }

    private User someUser() {
        return userRepository.findByEmail(registerUser().email()).orElseThrow();
    }

    private Reservation save(Reservation reservation) {
        return reservationRepository.saveAndFlush(reservation);
    }

    private Reservation reservation(User user, Space space, OffsetDateTime start, OffsetDateTime end) {
        return new Reservation(user, space, start, end, 2, BigDecimal.TEN, "tok_ok");
    }

    @Test
    void sumsOnlyConfirmedAndCompletedHoursClippedToTheRange() throws Exception {
        OffsetDateTime day = midnight(60);
        User user = someUser();
        Space busy = spaceRepository.findById(createSpace(adminToken, 10)).orElseThrow();
        Space empty = spaceRepository.findById(createSpace(adminToken, 10)).orElseThrow();

        Reservation confirmed = reservation(user, busy, day.plusHours(10), day.plusHours(12));
        confirmed.confirm();
        save(confirmed);

        Reservation completed = reservation(user, busy, day.plusHours(13), day.plusHours(14));
        completed.confirm();
        completed.complete(OffsetDateTime.now(ZoneOffset.UTC));
        save(completed);

        // empieza el dia anterior: solo la hora que cae dentro del rango debe contar
        Reservation straddling = reservation(user, busy, day.minusHours(1), day.plusHours(1));
        straddling.confirm();
        save(straddling);

        Reservation cancelled = reservation(user, busy, day.plusHours(15), day.plusHours(16));
        cancelled.cancel(OffsetDateTime.now(ZoneOffset.UTC));
        save(cancelled);
        save(reservation(user, busy, day.plusHours(16), day.plusHours(17)));

        ResponseEntity<String> response = report(adminToken, day.toInstant().toString(), day.plusDays(1).toInstant().toString());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode busyRow = rowFor(response, busy.getId());
        assertThat(busyRow.get("reservedHours").decimalValue()).isEqualByComparingTo("4.00");
        assertThat(busyRow.get("availableHours").decimalValue()).isEqualByComparingTo("24.00");
        assertThat(busyRow.get("occupancyPercentage").decimalValue()).isEqualByComparingTo("16.67");
        JsonNode emptyRow = rowFor(response, empty.getId());
        assertThat(emptyRow.get("reservedHours").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(emptyRow.get("occupancyPercentage").decimalValue()).isEqualByComparingTo("0.00");
    }

    @Test
    void onlyAdminCanReadTheReport() {
        String from = midnight(70).toInstant().toString();
        String to = midnight(69).toInstant().toString();

        assertThat(report(null, from, to).getStatusCode().value()).isEqualTo(401);
        assertThat(report(registerUser().token(), from, to).getStatusCode().value()).isEqualTo(403);
        assertThat(report(adminToken, from, to).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void invalidRangesReturn400() {
        String day = midnight(80).toInstant().toString();
        String longer = midnight(80).minusDays(400).toInstant().toString();

        assertThat(report(adminToken, day, day).getStatusCode().value()).isEqualTo(400);
        assertThat(report(adminToken, longer, day).getStatusCode().value()).isEqualTo(400);
        ResponseEntity<String> missing = rest.exchange(URI.create(rest.getRootUri() + "/api/v1/reports/occupancy"),
                HttpMethod.GET, new HttpEntity<>(authHeaders(adminToken)), String.class);
        assertThat(missing.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void secondCallIsServedFromTheCache() {
        String from = midnight(90).toInstant().toString();
        String to = midnight(89).toInstant().toString();

        report(adminToken, from, to);
        report(adminToken, from, to);

        verify(reservationRepository, times(1)).findOccupancy(any(), any());
    }

    @Test
    void sameInstantsWrittenWithADifferentOffsetShareTheCacheEntry() {
        OffsetDateTime from = midnight(100);
        OffsetDateTime to = midnight(99);

        report(adminToken, from.toInstant().toString(), to.toInstant().toString());
        report(adminToken, from.toString().replace("Z", "%2B00:00"), to.toString().replace("Z", "%2B00:00"));

        verify(reservationRepository, times(1)).findOccupancy(any(), any());
    }

    @Test
    void confirmingCancellingAndCompletingEvictTheCache() {
        String from = midnight(110).toInstant().toString();
        String to = midnight(109).toInstant().toString();
        TestUser owner = registerUser();
        UUID spaceId = createSpace(adminToken, 10);

        report(adminToken, from, to);
        verify(reservationRepository, times(1)).findOccupancy(any(), any());

        String toConfirm = field(createReservation(owner.token(), spaceId, futureHour(8, 9), futureHour(8, 10), 2), "id");
        ResponseEntity<String> confirmation = send(HttpMethod.POST, "/api/v1/reservations/" + toConfirm + "/confirm", owner.token(), null);
        assertThat(confirmation.getStatusCode().value()).isEqualTo(200);
        report(adminToken, from, to);
        verify(reservationRepository, times(2)).findOccupancy(any(), any());

        String toCancel = field(createReservation(owner.token(), spaceId, futureHour(8, 11), futureHour(8, 12), 2), "id");
        send(HttpMethod.POST, "/api/v1/reservations/" + toCancel + "/cancel", owner.token(), null);
        report(adminToken, from, to);
        verify(reservationRepository, times(3)).findOccupancy(any(), any());

        User user = userRepository.findByEmail(owner.email()).orElseThrow();
        Space space = spaceRepository.findById(spaceId).orElseThrow();
        OffsetDateTime end = OffsetDateTime.now(ZoneOffset.UTC).minusHours(2);
        Reservation finished = reservation(user, space, end.minusHours(1), end);
        finished.confirm();
        UUID finishedId = save(finished).getId();
        send(HttpMethod.POST, "/api/v1/reservations/" + finishedId + "/complete", adminToken, null);
        report(adminToken, from, to);
        verify(reservationRepository, times(4)).findOccupancy(any(), any());
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
