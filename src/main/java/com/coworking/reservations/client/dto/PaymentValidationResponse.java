package com.coworking.reservations.client.dto;

public record PaymentValidationResponse(
        boolean approved,
        String transactionId,
        String reason) {
}
