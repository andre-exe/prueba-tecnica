package com.coworking.reservations.domain.state;

import com.coworking.reservations.domain.entity.Reservation;
import com.coworking.reservations.domain.enums.ReservationStatus;
import com.coworking.reservations.exception.InvalidReservationStateException;

import java.time.OffsetDateTime;
import java.util.Set;

public interface ReservationState {

    default ReservationStatus confirm(Reservation reservation) {
        throw invalid("confirmar", reservation);
    }

    default ReservationStatus markPendingPayment(Reservation reservation) {
        throw invalid("dejar pendiente de pago", reservation);
    }

    default ReservationStatus cancel(Reservation reservation, OffsetDateTime now) {
        throw invalid("cancelar", reservation);
    }

    default ReservationStatus complete(Reservation reservation, OffsetDateTime now) {
        throw invalid("completar", reservation);
    }

    Set<ReservationStatus> allowedTransitions();

    private static InvalidReservationStateException invalid(String action, Reservation reservation) {
        return new InvalidReservationStateException(
                "No se puede " + action + " una reserva en estado " + reservation.getStatus());
    }
}
