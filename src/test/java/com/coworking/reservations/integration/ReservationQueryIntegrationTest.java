package com.coworking.reservations.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationQueryIntegrationTest extends AbstractIntegrationTest {

    private String admin;
    private TestUser owner;
    private UUID spaceId;
    private String pendingId;
    private String confirmedId;
    private String cancelledId;
    private OffsetDateTime day1;
    private OffsetDateTime day2;
    private OffsetDateTime day3;

    @BeforeEach
    void setUp() {
        admin = adminToken();
        owner = registerUser();
        spaceId = createSpace(admin, 10);
        day1 = futureHour(10, 9);
        day2 = futureHour(11, 9);
        day3 = futureHour(12, 9);
        pendingId = field(createReservation(owner.token(), spaceId, day1, day1.plusHours(1), 2), "id");
        confirmedId = field(createReservation(owner.token(), spaceId, day2, day2.plusHours(1), 2), "id");
        cancelledId = field(createReservation(owner.token(), spaceId, day3, day3.plusHours(1), 2), "id");
        send(HttpMethod.POST, "/api/v1/reservations/" + confirmedId + "/confirm", owner.token(), null);
        send(HttpMethod.POST, "/api/v1/reservations/" + cancelledId + "/cancel", owner.token(), null);
    }

    private JsonNode list(String query, String token) throws Exception {
        URI uri = URI.create(rest.getRootUri() + "/api/v1/reservations?spaceId=" + spaceId + query);
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String> response = rest.exchange(uri, HttpMethod.GET, new org.springframework.http.HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return objectMapper.readTree(response.getBody());
    }

    private List<String> ids(JsonNode page) {
        List<String> ids = new ArrayList<>();
        page.get("content").forEach(row -> ids.add(row.get("id").asText()));
        return ids;
    }

    @Test
    void theDefaultOrderIsTheStartTimeFromTheLatestToTheEarliest() throws Exception {
        assertThat(ids(list("", owner.token()))).containsExactly(cancelledId, confirmedId, pendingId);
    }

    @Test
    void theOrderCanBeAscending() throws Exception {
        assertThat(ids(list("&sort=startTime,asc", owner.token()))).containsExactly(pendingId, confirmedId, cancelledId);
    }

    @Test
    void filtersByStatus() throws Exception {
        assertThat(ids(list("&status=PENDING", owner.token()))).containsExactly(pendingId);
        assertThat(ids(list("&status=CONFIRMED", owner.token()))).containsExactly(confirmedId);
        assertThat(ids(list("&status=CANCELLED", owner.token()))).containsExactly(cancelledId);
        assertThat(ids(list("&status=COMPLETED", owner.token()))).isEmpty();
    }

    @Test
    void filtersByTheRangeOfStartTimesWithInclusiveLimits() throws Exception {
        String from = "&from=" + day2.toInstant();
        String to = "&to=" + day2.toInstant();

        assertThat(ids(list(from, owner.token()))).containsExactlyInAnyOrder(confirmedId, cancelledId);
        assertThat(ids(list(to, owner.token()))).containsExactlyInAnyOrder(pendingId, confirmedId);
        assertThat(ids(list(from + to, owner.token()))).containsExactly(confirmedId);
    }

    @Test
    void filtersCanBeCombinedAndTheOtherSpacesAreLeftOut() throws Exception {
        UUID otherSpace = createSpace(admin, 10);
        createReservation(owner.token(), otherSpace, day1, day1.plusHours(1), 2);

        assertThat(ids(list("&status=PENDING&from=" + day1.toInstant(), owner.token()))).containsExactly(pendingId);
        assertThat(list("", admin).get("totalElements").asInt()).isEqualTo(3);
    }

    @Test
    void theListIsPaginated() throws Exception {
        JsonNode first = list("&size=2&page=0", owner.token());
        JsonNode second = list("&size=2&page=1", owner.token());

        assertThat(first.get("content")).hasSize(2);
        assertThat(second.get("content")).hasSize(1);
        assertThat(first.get("totalElements").asInt()).isEqualTo(3);
        assertThat(first.get("totalPages").asInt()).isEqualTo(2);
    }

    @Test
    void theResponseCarriesTheSpaceAndUserDataWithoutExtraLookups() throws Exception {
        JsonNode row = list("&status=CONFIRMED", owner.token()).get("content").get(0);

        assertThat(row.get("spaceId").asText()).isEqualTo(spaceId.toString());
        assertThat(row.get("spaceName").asText()).startsWith("Sala ");
        assertThat(row.get("userEmail").asText()).isEqualTo(owner.email());
        assertThat(row.get("startTime").asText()).endsWith("Z");
        assertThat(row.get("updatedAt").asText()).endsWith("Z");
    }

    @Test
    void invalidSortAndStatusReturn400() {
        assertThat(send(HttpMethod.GET, "/api/v1/reservations?sort=nada", owner.token(), null).getStatusCode().value()).isEqualTo(400);
        assertThat(send(HttpMethod.GET, "/api/v1/reservations?status=XX", owner.token(), null).getStatusCode().value()).isEqualTo(400);
        assertThat(send(HttpMethod.GET, "/api/v1/reservations?from=ayer", owner.token(), null).getStatusCode().value()).isEqualTo(400);
    }
}
