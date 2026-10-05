package com.coworking.reservations.exception;

import org.springframework.http.HttpStatus;

public class InvalidDateRangeException extends BusinessException {

    public InvalidDateRangeException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", message);
    }
}
