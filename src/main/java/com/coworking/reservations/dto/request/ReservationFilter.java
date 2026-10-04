package com.coworking.reservations.dto.request;

import com.coworking.reservations.domain.enums.ReservationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReservationFilter(
        ReservationStatus status,
        UUID spaceId,
        UUID userId,
        OffsetDateTime from,
        OffsetDateTime to) {
}
