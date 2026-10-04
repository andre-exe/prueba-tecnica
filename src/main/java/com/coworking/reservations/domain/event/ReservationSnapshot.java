package com.coworking.reservations.domain.event;

import com.coworking.reservations.domain.entity.Reservation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

// los eventos llevan datos planos porque el listener corre en otro hilo, sin sesion de base de datos
public record ReservationSnapshot(
        UUID reservationId,
        String userEmail,
        String userName,
        String spaceName,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        BigDecimal totalPrice) {

    public static ReservationSnapshot of(Reservation reservation) {
        return new ReservationSnapshot(
                reservation.getId(),
                reservation.getUser().getEmail(),
                reservation.getUser().getFullName(),
                reservation.getSpace().getName(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getTotalPrice());
    }
}
