package com.coworking.reservations.domain.event;

public record ReservationCancelledEvent(ReservationSnapshot reservation) {
}
