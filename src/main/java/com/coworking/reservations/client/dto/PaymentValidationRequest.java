package com.coworking.reservations.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentValidationRequest(
        UUID reservationId,
        BigDecimal amount,
        String currency,
        String paymentMethod) {
}
