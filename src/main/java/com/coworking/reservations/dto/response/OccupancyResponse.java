package com.coworking.reservations.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record OccupancyResponse(
        UUID spaceId,
        String spaceName,
        BigDecimal reservedHours,
        BigDecimal availableHours,
        BigDecimal occupancyPercentage) {
}
