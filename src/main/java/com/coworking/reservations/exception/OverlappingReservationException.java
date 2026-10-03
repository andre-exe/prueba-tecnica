package com.coworking.reservations.exception;

import org.springframework.http.HttpStatus;

public class OverlappingReservationException extends BusinessException {

    private static final String MESSAGE = "El espacio ya esta reservado en ese horario";

    public OverlappingReservationException() {
        super(HttpStatus.CONFLICT, "RESERVATION_OVERLAP", MESSAGE);
    }

    public OverlappingReservationException(Throwable cause) {
        super(HttpStatus.CONFLICT, "RESERVATION_OVERLAP", MESSAGE, cause);
    }
}
