package com.coworking.reservations.exception;

import org.springframework.http.HttpStatus;

public class DuplicateSpaceNameException extends BusinessException {

    public DuplicateSpaceNameException(String name) {
        super(HttpStatus.CONFLICT, "DUPLICATE_SPACE_NAME", "Ya existe un espacio con el nombre " + name);
    }
}
