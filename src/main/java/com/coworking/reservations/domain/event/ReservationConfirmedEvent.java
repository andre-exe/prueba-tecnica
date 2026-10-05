package com.coworking.reservations.domain.event;

public record ReservationConfirmedEvent(ReservationSnapshot reservation) {
}
