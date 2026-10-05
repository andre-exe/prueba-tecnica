package com.coworking.reservations.dto.response;

import com.coworking.reservations.domain.enums.Role;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        Role role,
        OffsetDateTime createdAt) {
}
