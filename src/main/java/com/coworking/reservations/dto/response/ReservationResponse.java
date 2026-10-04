package com.coworking.reservations.dto.response;

import com.coworking.reservations.domain.enums.ReservationStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        UUID spaceId,
        String spaceName,
        UUID userId,
        String userEmail,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        int attendees,
        ReservationStatus status,
        BigDecimal totalPrice,
        String paymentMethod,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
