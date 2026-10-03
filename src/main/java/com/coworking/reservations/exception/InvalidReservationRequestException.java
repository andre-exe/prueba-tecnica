package com.coworking.reservations.exception;

import org.springframework.http.HttpStatus;

public class InvalidReservationRequestException extends BusinessException {

    public InvalidReservationRequestException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_RESERVATION_REQUEST", message);
    }
}
