package com.coworking.reservations.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateReservationRequest(
        @NotNull UUID spaceId,
        @NotNull OffsetDateTime startTime,
        @NotNull OffsetDateTime endTime,
        @NotNull @Min(1) Integer attendees,
        @NotBlank @Size(max = 100) String paymentMethod) {
}
