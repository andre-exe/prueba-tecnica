package com.coworking.reservations.service;

import com.coworking.reservations.config.properties.ReservationProperties;
import com.coworking.reservations.domain.entity.Space;
import com.coworking.reservations.dto.request.CreateReservationRequest;
import com.coworking.reservations.exception.InvalidReservationRequestException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;

@Component
public class ReservationValidator {

    private final ReservationProperties properties;

    public ReservationValidator(ReservationProperties properties) {
        this.properties = properties;
    }

    public void validate(CreateReservationRequest request, Space space, OffsetDateTime now) {
        OffsetDateTime start = request.startTime();
        OffsetDateTime end = request.endTime();

        if (!end.isAfter(start)) {
            throw new InvalidReservationRequestException("La hora de fin debe ser posterior a la de inicio");
        }
        if (!start.isAfter(now)) {
            throw new InvalidReservationRequestException("La reserva debe empezar en el futuro");
        }
        if (start.isAfter(now.plusDays(properties.maxAdvanceDays()))) {
            throw new InvalidReservationRequestException(
                    "Solo se puede reservar con hasta " + properties.maxAdvanceDays() + " dias de anticipacion");
        }
        Duration duration = Duration.between(start, end);
        if (duration.compareTo(properties.minDuration()) < 0) {
            throw new InvalidReservationRequestException(
                    "La duracion minima es de " + properties.minDuration().toMinutes() + " minutos");
        }
        if (duration.compareTo(properties.maxDuration()) > 0) {
            throw new InvalidReservationRequestException(
                    "La duracion maxima es de " + properties.maxDuration().toHours() + " horas");
        }
        if (!space.isActive()) {
            throw new InvalidReservationRequestException("El espacio no esta disponible para reservas");
        }
        if (request.attendees() > space.getCapacity()) {
            throw new InvalidReservationRequestException(
                    "El espacio tiene capacidad para " + space.getCapacity() + " personas");
        }
    }
}
