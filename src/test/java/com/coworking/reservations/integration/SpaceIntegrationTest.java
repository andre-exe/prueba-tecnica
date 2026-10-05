package com.coworking.reservations.integration;

import com.coworking.reservations.domain.entity.Space;
import com.coworking.reservations.repository.SpaceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SpaceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SpaceRepository spaceRepository;

    private String admin;
    private String user;
    private String tag;

    @BeforeEach
    void setUp() {
        admin = adminToken();
        user = registerUser().token();
        tag = UUID.randomUUID().toString().substring(0, 8);
    }

    private Map<String, Object> body(String name, String type, int capacity, String location, Object rate) {
        return Map.of("name", name, "type", type, "capacity", capacity, "location", location, "hourlyRate", rate);
    }

    private String create(String name, String type, int capacity, String location) {
        ResponseEntity<String> response = send(HttpMethod.POST, "/api/v1/spaces", admin, body(name, type, capacity, location, 10));
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        return field(response, "id");
    }

    private JsonNode page(String query, String token) throws Exception {
        return objectMapper.readTree(send(HttpMethod.GET, "/api/v1/spaces?" + query, token, null).getBody());
    }

    private List<String> names(JsonNode page) {
        List<String> names = new ArrayList<>();
        page.get("content").forEach(row -> names.add(row.get("name").asText()));
        return names;
    }

    @Test
    void createReturnsTheSpaceWithItsLocationHeaderAndDefaults() {
        ResponseEntity<String> response = send(HttpMethod.POST, "/api/v1/spaces", admin,
                body("Sala " + tag, "MEETING_ROOM", 8, "Piso 2 " + tag, 15.5));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getHeaders().getLocation().toString()).endsWith("/api/v1/spaces/" + field(response, "id"));
        assertThat(field(response, "active")).isEqualTo("true");
        assertThat(new java.math.BigDecimal(field(response, "hourlyRate"))).isEqualByComparingTo("15.50");
    }

    @Test
    void duplicatedNamesAreRejectedOnCreateAndOnUpdate() {
        create("Unica " + tag, "HOT_DESK", 1, "Piso " + tag);
        String other = create("Otra " + tag, "HOT_DESK", 1, "Piso " + tag);

        ResponseEntity<String> duplicate = send(HttpMethod.POST, "/api/v1/spaces", admin,
                body("Unica " + tag, "HOT_DESK", 1, "Piso 1", 5));
        ResponseEntity<String> renamed = send(HttpMethod.PUT, "/api/v1/spaces/" + other, admin,
                body("Unica " + tag, "HOT_DESK", 1, "Piso 1", 5));

        assertThat(duplicate.getStatusCode().value()).isEqualTo(409);
        assertThat(field(duplicate, "code")).isEqualTo("DUPLICATE_SPACE_NAME");
        assertThat(renamed.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void anUpdateKeepingTheSameNameIsAllowed() {
        String id = create("Misma " + tag, "HOT_DESK", 1, "Piso 1");

        ResponseEntity<String> response = send(HttpMethod.PUT, "/api/v1/spaces/" + id, admin,
                body("Misma " + tag, "PRIVATE_OFFICE", 6, "Piso 3", 40));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(field(response, "type")).isEqualTo("PRIVATE_OFFICE");
        assertThat(field(response, "capacity")).isEqualTo("6");
    }

    @Test
    void invalidDataReturnsOneErrorPerField() throws Exception {
        ResponseEntity<String> response = send(HttpMethod.POST, "/api/v1/spaces", admin, body("", "HOT_DESK", 0, "", -1));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(objectMapper.readTree(response.getBody()).get("errors")).hasSize(4);
    }

    @Test
    void filtersCombineAndTheLocationSearchIgnoresCase() throws Exception {
        create("Sala Grande " + tag, "MEETING_ROOM", 12, "Torre " + tag);
        create("Sala Chica " + tag, "MEETING_ROOM", 3, "Torre " + tag);
        create("Puesto " + tag, "HOT_DESK", 1, "Torre " + tag);

        assertThat(names(page("location=torre " + tag.toUpperCase(), user))).hasSize(3);
        assertThat(names(page("location=" + tag + "&type=MEETING_ROOM", user)))
                .containsExactlyInAnyOrder("Sala Grande " + tag, "Sala Chica " + tag);
        assertThat(names(page("location=" + tag + "&type=MEETING_ROOM&minCapacity=5", user)))
                .containsExactly("Sala Grande " + tag);
        assertThat(names(page("location=" + tag + "&minCapacity=100", user))).isEmpty();
    }

    @Test
    void aWildcardCharacterInTheLocationIsSearchedLiterally() throws Exception {
        create("Con porcentaje " + tag, "HOT_DESK", 1, "Zona 100% " + tag);
        create("Sin porcentaje " + tag, "HOT_DESK", 1, "Zona 100 " + tag);

        assertThat(names(page("location=100% " + tag, user))).containsExactly("Con porcentaje " + tag);
    }

    @Test
    void resultsArePaginatedSortedByNameAndTheSizeIsCapped() throws Exception {
        create("A-" + tag, "HOT_DESK", 1, "Pag " + tag);
        create("B-" + tag, "HOT_DESK", 1, "Pag " + tag);
        create("C-" + tag, "HOT_DESK", 1, "Pag " + tag);

        JsonNode first = page("location=Pag " + tag + "&size=2&page=0", user);
        JsonNode second = page("location=Pag " + tag + "&size=2&page=1", user);

        assertThat(names(first)).containsExactly("A-" + tag, "B-" + tag);
        assertThat(names(second)).containsExactly("C-" + tag);
        assertThat(first.get("totalElements").asInt()).isEqualTo(3);
        assertThat(first.get("totalPages").asInt()).isEqualTo(2);
        assertThat(page("size=1000", user).get("size").asInt()).isEqualTo(50);
        assertThat(page("", user).get("size").asInt()).isEqualTo(20);
        assertThat(names(page("location=Pag " + tag + "&sort=name,desc", user)))
                .containsExactly("C-" + tag, "B-" + tag, "A-" + tag);
    }

    @Test
    void invalidSortAndInvalidFiltersReturn400() {
        assertThat(send(HttpMethod.GET, "/api/v1/spaces?sort=noexiste,asc", user, null).getStatusCode().value()).isEqualTo(400);
        assertThat(send(HttpMethod.GET, "/api/v1/spaces?type=XX", user, null).getStatusCode().value()).isEqualTo(400);
        assertThat(send(HttpMethod.GET, "/api/v1/spaces/no-es-uuid", user, null).getStatusCode().value()).isEqualTo(400);
        assertThat(send(HttpMethod.GET, "/api/v1/spaces/" + UUID.randomUUID(), user, null).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void deletingIsLogicalAndInactiveSpacesAreOnlyVisibleToAdmins() throws Exception {
        String id = create("Borrable " + tag, "HOT_DESK", 1, "Del " + tag);

        assertThat(send(HttpMethod.DELETE, "/api/v1/spaces/" + id, admin, null).getStatusCode().value()).isEqualTo(204);
        assertThat(send(HttpMethod.DELETE, "/api/v1/spaces/" + id, admin, null).getStatusCode().value()).isEqualTo(204);

        // la fila sigue en la base, solo cambia el estado
        Space stored = spaceRepository.findById(UUID.fromString(id)).orElseThrow();
        assertThat(stored.isActive()).isFalse();
        // un usuario normal ya no lo ve ni en la lista ni por id, y no puede pedir los inactivos
        assertThat(names(page("location=Del " + tag, user))).isEmpty();
        assertThat(names(page("location=Del " + tag + "&active=false", user))).isEmpty();
        assertThat(send(HttpMethod.GET, "/api/v1/spaces/" + id, user, null).getStatusCode().value()).isEqualTo(404);
        // el admin sí
        assertThat(names(page("location=Del " + tag, admin))).containsExactly("Borrable " + tag);
        assertThat(names(page("location=Del " + tag + "&active=false", admin))).containsExactly("Borrable " + tag);
        assertThat(names(page("location=Del " + tag + "&active=true", admin))).isEmpty();
        assertThat(send(HttpMethod.GET, "/api/v1/spaces/" + id, admin, null).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void deletingASpaceThatDoesNotExistReturns404() {
        assertThat(send(HttpMethod.DELETE, "/api/v1/spaces/" + UUID.randomUUID(), admin, null).getStatusCode().value()).isEqualTo(404);
        assertThat(send(HttpMethod.PUT, "/api/v1/spaces/" + UUID.randomUUID(), admin,
                body("X " + tag, "HOT_DESK", 1, "Piso 1", 5)).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void aReservationCannotBeMadeInAnInactiveSpace() {
        String id = create("Cerrada " + tag, "MEETING_ROOM", 8, "Cer " + tag);
        send(HttpMethod.DELETE, "/api/v1/spaces/" + id, admin, null);

        ResponseEntity<String> response = createReservation(user, UUID.fromString(id), futureHour(9, 9), futureHour(9, 10), 2);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(field(response, "detail")).contains("no esta disponible");
    }
}
