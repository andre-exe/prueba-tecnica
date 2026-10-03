package com.coworking.reservations.exception;

import org.springframework.http.HttpStatus;

public class InvalidReservationStateException extends BusinessException {

    public InvalidReservationStateException(String message) {
        super(HttpStatus.CONFLICT, "INVALID_RESERVATION_STATE", message);
    }
}
