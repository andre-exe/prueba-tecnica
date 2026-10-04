package com.coworking.reservations.domain.enums;

import com.coworking.reservations.domain.state.CancelledState;
import com.coworking.reservations.domain.state.CompletedState;
import com.coworking.reservations.domain.state.ConfirmedState;
import com.coworking.reservations.domain.state.PendingPaymentState;
import com.coworking.reservations.domain.state.PendingState;
import com.coworking.reservations.domain.state.ReservationState;

public enum ReservationStatus {
    PENDING(new PendingState()),
    PENDING_PAYMENT(new PendingPaymentState()),
    CONFIRMED(new ConfirmedState()),
    CANCELLED(new CancelledState()),
    COMPLETED(new CompletedState());

    private final ReservationState state;

    ReservationStatus(ReservationState state) {
        this.state = state;
    }

    public ReservationState state() {
        return state;
    }
}
