package com.coworking.reservations.exception;

import org.springframework.http.HttpStatus;

public class PaymentDeclinedException extends BusinessException {

    private final String reason;

    public PaymentDeclinedException(String reason) {
        super(HttpStatus.PAYMENT_REQUIRED, "PAYMENT_DECLINED", "El pago fue rechazado: " + reason);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
