package com.coworking.reservations.domain.event;

import java.util.UUID;

public record ReservationCompletedEvent(UUID reservationId) {
}
