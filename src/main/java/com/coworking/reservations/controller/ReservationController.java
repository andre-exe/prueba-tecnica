package com.coworking.reservations.controller;

import com.coworking.reservations.domain.enums.ReservationStatus;
import com.coworking.reservations.dto.request.CreateReservationRequest;
import com.coworking.reservations.dto.request.ReservationFilter;
import com.coworking.reservations.dto.response.PageResponse;
import com.coworking.reservations.dto.response.ReservationResponse;
import com.coworking.reservations.security.CurrentUser;
import com.coworking.reservations.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> create(@Valid @RequestBody CreateReservationRequest request,
                                                      Authentication authentication) {
        // la reserva siempre queda a nombre de quien esta logueado, el body no trae usuario
        ReservationResponse created = reservationService.create(CurrentUser.from(authentication).id(), request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri()).body(created);
    }

    @GetMapping
    public PageResponse<ReservationResponse> list(
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(required = false) UUID spaceId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @PageableDefault(size = 20, sort = "startTime", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        return reservationService.search(new ReservationFilter(status, spaceId, userId, from, to),
                pageable, CurrentUser.from(authentication));
    }

    @GetMapping("/{id}")
    public ReservationResponse get(@PathVariable UUID id, Authentication authentication) {
        return reservationService.findById(id, CurrentUser.from(authentication));
    }

    @PostMapping("/{id}/cancel")
    public ReservationResponse cancel(@PathVariable UUID id, Authentication authentication) {
        return reservationService.cancel(id, CurrentUser.from(authentication));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('ADMIN')")
    public ReservationResponse complete(@PathVariable UUID id) {
        return reservationService.complete(id);
    }
}
