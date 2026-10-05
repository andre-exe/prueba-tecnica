package com.coworking.reservations.dto.response;

public record ConfirmReservationResponse(
        String message,
        ReservationResponse reservation) {
}
