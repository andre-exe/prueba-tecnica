package com.coworking.reservations.domain.state;

import com.coworking.reservations.domain.entity.Reservation;
import com.coworking.reservations.domain.entity.Space;
import com.coworking.reservations.domain.entity.User;
import com.coworking.reservations.domain.enums.ReservationStatus;
import com.coworking.reservations.domain.enums.Role;
import com.coworking.reservations.domain.enums.SpaceType;
import com.coworking.reservations.exception.InvalidReservationStateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.stream.Stream;

import static com.coworking.reservations.domain.enums.ReservationStatus.CANCELLED;
import static com.coworking.reservations.domain.enums.ReservationStatus.COMPLETED;
import static com.coworking.reservations.domain.enums.ReservationStatus.CONFIRMED;
import static com.coworking.reservations.domain.enums.ReservationStatus.PENDING;
import static com.coworking.reservations.domain.enums.ReservationStatus.PENDING_PAYMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationStateTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2030, 6, 1, 12, 0, 0, 0, ZoneOffset.UTC);

    enum Action {
        CONFIRM, PENDING_PAYMENT, CANCEL, COMPLETE
    }

    // futura: aun no empieza, en curso: ya empezo y no termina, terminada: ya paso
    enum Window {
        FUTURE(NOW.plusHours(1), NOW.plusHours(2)),
        ONGOING(NOW.minusHours(1), NOW.plusHours(1)),
        ENDED(NOW.minusHours(3), NOW.minusHours(2));

        final OffsetDateTime start;
        final OffsetDateTime end;

        Window(OffsetDateTime start, OffsetDateTime end) {
            this.start = start;
            this.end = end;
        }
    }

    static Stream<Arguments> fullMatrix() {
        Stream.Builder<Arguments> builder = Stream.builder();
        for (ReservationStatus from : ReservationStatus.values()) {
            for (Action action : Action.values()) {
                for (Window window : Window.values()) {
                    builder.add(Arguments.of(from, action, window, expectedTarget(from, action, window)));
                }
            }
        }
        return builder.build();
    }

    // tabla de transiciones validas, todo lo que no aparezca aqui debe fallar
    private static ReservationStatus expectedTarget(ReservationStatus from, Action action, Window window) {
        return switch (from) {
            case PENDING -> switch (action) {
                case CONFIRM -> CONFIRMED;
                case PENDING_PAYMENT -> ReservationStatus.PENDING_PAYMENT;
                case CANCEL -> CANCELLED;
                case COMPLETE -> null;
            };
            case PENDING_PAYMENT -> switch (action) {
                case CONFIRM -> CONFIRMED;
                case PENDING_PAYMENT -> ReservationStatus.PENDING_PAYMENT;
                case CANCEL -> CANCELLED;
                case COMPLETE -> null;
            };
            case CONFIRMED -> switch (action) {
                case CANCEL -> window == Window.FUTURE ? CANCELLED : null;
                case COMPLETE -> window == Window.ENDED ? COMPLETED : null;
                case CONFIRM, PENDING_PAYMENT -> null;
            };
            case CANCELLED, COMPLETED -> null;
        };
    }

    private Reservation reservation(ReservationStatus status, Window window) {
        User user = new User("ana@test.com", "hash", "Ana", Role.USER);
        Space space = new Space("Sala", SpaceType.MEETING_ROOM, 4, "Piso 1", BigDecimal.TEN);
        Reservation reservation = new Reservation(user, space, window.start, window.end, 2, BigDecimal.TEN, "tok_ok");
        ReflectionTestUtils.setField(reservation, "status", status);
        return reservation;
    }

    private void apply(Reservation reservation, Action action) {
        switch (action) {
            case CONFIRM -> reservation.confirm();
            case PENDING_PAYMENT -> reservation.markPendingPayment();
            case CANCEL -> reservation.cancel(NOW);
            case COMPLETE -> reservation.complete(NOW);
        }
    }

    @ParameterizedTest(name = "{0} + {1} ({2}) -> {3}")
    @MethodSource("fullMatrix")
    void followsTheTransitionMatrix(ReservationStatus from, Action action, Window window, ReservationStatus expected) {
        Reservation reservation = reservation(from, window);

        if (expected == null) {
            assertThatThrownBy(() -> apply(reservation, action)).isInstanceOf(InvalidReservationStateException.class);
            assertThat(reservation.getStatus()).isEqualTo(from);
        } else {
            apply(reservation, action);
            assertThat(reservation.getStatus()).isEqualTo(expected);
        }
    }

    @Test
    void newReservationStartsPending() {
        assertThat(reservation(PENDING, Window.FUTURE).getStatus()).isEqualTo(PENDING);
        User user = new User("a@a.com", "h", "A", Role.USER);
        Space space = new Space("S", SpaceType.HOT_DESK, 1, "P", BigDecimal.ONE);
        Reservation created = new Reservation(user, space, NOW.plusHours(1), NOW.plusHours(2), 1, BigDecimal.ONE, "tok_ok");
        assertThat(created.getStatus()).isEqualTo(PENDING);
    }

    @Test
    void finalStatesAllowNoTransitions() {
        assertThat(CANCELLED.state().allowedTransitions()).isEmpty();
        assertThat(COMPLETED.state().allowedTransitions()).isEmpty();
    }

    @Test
    void allowedTransitionsDescribeEachState() {
        assertThat(PENDING.state().allowedTransitions()).isEqualTo(Set.of(CONFIRMED, PENDING_PAYMENT, CANCELLED));
        assertThat(PENDING_PAYMENT.state().allowedTransitions()).isEqualTo(Set.of(CONFIRMED, PENDING_PAYMENT, CANCELLED));
        assertThat(CONFIRMED.state().allowedTransitions()).isEqualTo(Set.of(CANCELLED, COMPLETED));
    }

    @Test
    void everyStatusResolvesItsOwnState() {
        for (ReservationStatus status : ReservationStatus.values()) {
            assertThat(status.state()).isNotNull();
        }
    }

    @Test
    void errorMessageMentionsTheCurrentStatus() {
        Reservation reservation = reservation(CANCELLED, Window.FUTURE);

        assertThatThrownBy(reservation::confirm)
                .isInstanceOf(InvalidReservationStateException.class)
                .hasMessageContaining("CANCELLED");
    }
}
