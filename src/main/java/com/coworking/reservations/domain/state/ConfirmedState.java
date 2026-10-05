package com.coworking.reservations.domain.state;

import com.coworking.reservations.domain.entity.Reservation;
import com.coworking.reservations.domain.enums.ReservationStatus;
import com.coworking.reservations.exception.InvalidReservationStateException;

import java.time.OffsetDateTime;
import java.util.Set;

public class ConfirmedState implements ReservationState {

    @Override
    public ReservationStatus cancel(Reservation reservation, OffsetDateTime now) {
        if (!now.isBefore(reservation.getStartTime())) {
            throw new InvalidReservationStateException("No se puede cancelar una reserva que ya inició");
        }
        return ReservationStatus.CANCELLED;
    }

    @Override
    public ReservationStatus complete(Reservation reservation, OffsetDateTime now) {
        if (now.isBefore(reservation.getEndTime())) {
            throw new InvalidReservationStateException("No se puede completar una reserva que aún no termina");
        }
        return ReservationStatus.COMPLETED;
    }

    @Override
    public Set<ReservationStatus> allowedTransitions() {
        return Set.of(ReservationStatus.CANCELLED, ReservationStatus.COMPLETED);
    }
}
