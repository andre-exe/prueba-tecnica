package com.coworking.reservations.service;

import com.coworking.reservations.dto.request.CreateReservationRequest;
import com.coworking.reservations.dto.request.ReservationFilter;
import com.coworking.reservations.dto.response.PageResponse;
import com.coworking.reservations.dto.response.ReservationResponse;
import com.coworking.reservations.security.CurrentUser;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ReservationService {

    ReservationResponse create(UUID userId, CreateReservationRequest request);

    ReservationResponse findById(UUID id, CurrentUser requester);

    PageResponse<ReservationResponse> search(ReservationFilter filter, Pageable pageable, CurrentUser requester);

    ReservationResponse cancel(UUID id, CurrentUser requester);

    ReservationResponse complete(UUID id);

    int completeExpired();
}
