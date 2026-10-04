package com.coworking.reservations.integration;

import com.coworking.reservations.domain.entity.Reservation;
import com.coworking.reservations.domain.entity.Space;
import com.coworking.reservations.domain.entity.User;
import com.coworking.reservations.dto.request.ReservationFilter;
import com.coworking.reservations.repository.ReservationRepository;
import com.coworking.reservations.repository.SpaceRepository;
import com.coworking.reservations.repository.UserRepository;
import com.coworking.reservations.repository.specification.ReservationSpecifications;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final int THREADS = 10;

    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private SpaceRepository spaceRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    void tenSimultaneousRequestsForTheSameSlotCreateExactlyOne() throws Exception {
        UUID spaceId = createSpace(adminToken(), 10);
        String token = registerUser().token();
        OffsetDateTime start = futureHour(3, 10);

        List<Integer> statuses = fireAtTheSameTime(i -> createReservation(token, spaceId, start, start.plusHours(1), 2));

        Map<Integer, Long> byStatus = statuses.stream().collect(Collectors.groupingBy(s -> s, Collectors.counting()));
        assertThat(byStatus).containsEntry(201, 1L).containsEntry(409, 9L).hasSize(2);
        assertThat(reservationsInSpace(spaceId)).isEqualTo(1);
    }

    @Test
    void tenSimultaneousRequestsForDifferentSlotsAllSucceed() throws Exception {
        UUID spaceId = createSpace(adminToken(), 10);
        String token = registerUser().token();
        OffsetDateTime firstSlot = futureHour(4, 8);

        List<Integer> statuses = fireAtTheSameTime(i ->
                createReservation(token, spaceId, firstSlot.plusHours(i), firstSlot.plusHours(i + 1), 2));

        assertThat(statuses).hasSize(THREADS).containsOnly(201);
        assertThat(reservationsInSpace(spaceId)).isEqualTo(THREADS);
    }

    @Test
    void databaseConstraintRejectsAnOverlapEvenWithoutTheLock() {
        UUID spaceId = createSpace(adminToken(), 10);
        TestUser testUser = registerUser();
        User user = userRepository.findByEmail(testUser.email()).orElseThrow();
        Space space = spaceRepository.findById(spaceId).orElseThrow();
        OffsetDateTime start = futureHour(5, 10);

        reservationRepository.saveAndFlush(new Reservation(user, space, start, start.plusHours(2), 2, BigDecimal.TEN, "tok_ok"));
        Reservation overlapping = new Reservation(user, space, start.plusHours(1), start.plusHours(3), 2, BigDecimal.TEN, "tok_ok");

        assertThatThrownBy(() -> reservationRepository.saveAndFlush(overlapping))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(((SQLException) ((DataIntegrityViolationException) e).getMostSpecificCause()).getSQLState())
                        .isEqualTo("23P01"));
    }

    @Test
    void adjacentReservationsDoNotCollide() {
        UUID spaceId = createSpace(adminToken(), 10);
        String token = registerUser().token();
        OffsetDateTime start = futureHour(6, 10);

        ResponseEntity<String> first = createReservation(token, spaceId, start, start.plusHours(1), 2);
        ResponseEntity<String> second = createReservation(token, spaceId, start.plusHours(1), start.plusHours(2), 2);

        assertThat(first.getStatusCode().value()).isEqualTo(201);
        assertThat(second.getStatusCode().value()).isEqualTo(201);
    }

    private long reservationsInSpace(UUID spaceId) {
        return reservationRepository.count(ReservationSpecifications.withFilters(
                new ReservationFilter(null, spaceId, null, null, null)));
    }

    // todos los hilos esperan en la misma linea de salida y arrancan juntos
    private List<Integer> fireAtTheSameTime(java.util.function.IntFunction<ResponseEntity<String>> request) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < THREADS; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return request.apply(index).getStatusCode().value();
                }));
            }
            ready.await();
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get());
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }
}
