package com.coworking.reservations.dto.response;

public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn) {
}
