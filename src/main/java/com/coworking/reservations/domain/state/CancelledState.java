package com.coworking.reservations.domain.state;

import com.coworking.reservations.domain.enums.ReservationStatus;

import java.util.Set;

public class CancelledState implements ReservationState {

    @Override
    public Set<ReservationStatus> allowedTransitions() {
        return Set.of();
    }
}
