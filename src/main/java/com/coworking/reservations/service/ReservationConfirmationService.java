package com.coworking.reservations.service;

import com.coworking.reservations.dto.response.ConfirmReservationResponse;
import com.coworking.reservations.security.CurrentUser;

import java.util.UUID;

public interface ReservationConfirmationService {

    ConfirmReservationResponse confirm(UUID id, CurrentUser requester);
}
