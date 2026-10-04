package com.coworking.reservations.domain.event;

public record ReservationPendingPaymentEvent(ReservationSnapshot reservation) {
}
