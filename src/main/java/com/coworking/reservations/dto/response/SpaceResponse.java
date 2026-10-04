package com.coworking.reservations.dto.response;

import com.coworking.reservations.domain.enums.SpaceType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SpaceResponse(
        UUID id,
        String name,
        SpaceType type,
        int capacity,
        String location,
        BigDecimal hourlyRate,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
