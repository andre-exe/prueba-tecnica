package com.coworking.reservations.domain.state;

import com.coworking.reservations.domain.entity.Reservation;
import com.coworking.reservations.domain.enums.ReservationStatus;

import java.time.OffsetDateTime;
import java.util.Set;

public class PendingPaymentState implements ReservationState {

    @Override
    public ReservationStatus confirm(Reservation reservation) {
        return ReservationStatus.CONFIRMED;
    }

    // si el reintento tambien falla porque el proveedor sigue caido, se queda donde esta
    @Override
    public ReservationStatus markPendingPayment(Reservation reservation) {
        return ReservationStatus.PENDING_PAYMENT;
    }

    @Override
    public ReservationStatus cancel(Reservation reservation, OffsetDateTime now) {
        return ReservationStatus.CANCELLED;
    }

    @Override
    public Set<ReservationStatus> allowedTransitions() {
        return Set.of(ReservationStatus.CONFIRMED, ReservationStatus.PENDING_PAYMENT, ReservationStatus.CANCELLED);
    }
}
