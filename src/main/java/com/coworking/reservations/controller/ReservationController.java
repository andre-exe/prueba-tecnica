package com.coworking.reservations.controller;

import com.coworking.reservations.domain.enums.ReservationStatus;
import com.coworking.reservations.dto.request.CreateReservationRequest;
import com.coworking.reservations.dto.request.ReservationFilter;
import com.coworking.reservations.dto.response.ConfirmReservationResponse;
import com.coworking.reservations.dto.response.PageResponse;
import com.coworking.reservations.dto.response.ReservationResponse;
import com.coworking.reservations.security.CurrentUser;
import com.coworking.reservations.service.ReservationConfirmationService;
import com.coworking.reservations.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
@Tag(name = "Reservas", description = "Crear, consultar, confirmar, cancelar y completar reservas")
public class ReservationController {

    private final ReservationService reservationService;
    private final ReservationConfirmationService confirmationService;

    public ReservationController(ReservationService reservationService,
                                 ReservationConfirmationService confirmationService) {
        this.reservationService = reservationService;
        this.confirmationService = confirmationService;
    }

    @PostMapping
    @Operation(summary = "Crear una reserva",
            description = "Queda a nombre del usuario autenticado en estado PENDING. Un solape con otra reserva activa del mismo espacio da 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Reserva creada"),
            @ApiResponse(responseCode = "400", description = "Datos mal formados"),
            @ApiResponse(responseCode = "401", description = "Sin token"),
            @ApiResponse(responseCode = "404", description = "El espacio no existe"),
            @ApiResponse(responseCode = "409", description = "El espacio ya esta reservado en ese horario"),
            @ApiResponse(responseCode = "422", description = "Rompe una regla: fechas, duracion, capacidad o espacio inactivo")
    })
    public ResponseEntity<ReservationResponse> create(@Valid @RequestBody CreateReservationRequest request,
                                                      Authentication authentication) {
        // la reserva siempre queda a nombre de quien esta logueado, el body no trae usuario
        ReservationResponse created = reservationService.create(CurrentUser.from(authentication).id(), request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri()).body(created);
    }

    @GetMapping
    @Operation(summary = "Listar reservas",
            description = "Un usuario normal ve solo las suyas, el ADMIN ve todas y puede filtrar por userId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina de reservas"),
            @ApiResponse(responseCode = "400", description = "Filtro, orden o paginacion invalidos"),
            @ApiResponse(responseCode = "401", description = "Sin token")
    })
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
    @Operation(summary = "Obtener una reserva", description = "Si no es del usuario autenticado responde 404, salvo para ADMIN")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reserva encontrada"),
            @ApiResponse(responseCode = "401", description = "Sin token"),
            @ApiResponse(responseCode = "404", description = "No existe o es de otro usuario")
    })
    public ReservationResponse get(@PathVariable UUID id, Authentication authentication) {
        return reservationService.findById(id, CurrentUser.from(authentication));
    }

    // 200 si el pago se aprobo, 202 si el proveedor no respondio y la reserva quedo pendiente de pago
    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirmar una reserva",
            description = "Valida el pago con el proveedor externo, protegido por un circuit breaker. 202 significa que el proveedor no respondio y se puede reintentar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pago aprobado, reserva CONFIRMED"),
            @ApiResponse(responseCode = "202", description = "Proveedor caido o lento, reserva PENDING_PAYMENT"),
            @ApiResponse(responseCode = "401", description = "Sin token"),
            @ApiResponse(responseCode = "402", description = "Pago rechazado, la reserva sigue pendiente"),
            @ApiResponse(responseCode = "404", description = "No existe o es de otro usuario"),
            @ApiResponse(responseCode = "409", description = "El estado actual no permite confirmar")
    })
    public ResponseEntity<ConfirmReservationResponse> confirm(@PathVariable UUID id, Authentication authentication) {
        ConfirmReservationResponse response = confirmationService.confirm(id, CurrentUser.from(authentication));
        HttpStatus status = response.reservation().status() == ReservationStatus.CONFIRMED
                ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(response);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancelar una reserva",
            description = "El dueno o un ADMIN. Una reserva confirmada solo se cancela antes de que empiece.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reserva cancelada"),
            @ApiResponse(responseCode = "401", description = "Sin token"),
            @ApiResponse(responseCode = "404", description = "No existe o es de otro usuario"),
            @ApiResponse(responseCode = "409", description = "El estado actual no permite cancelar")
    })
    public ReservationResponse cancel(@PathVariable UUID id, Authentication authentication) {
        return reservationService.cancel(id, CurrentUser.from(authentication));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Completar una reserva", description = "Solo ADMIN, y solo si la reserva esta confirmada y ya termino")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reserva completada"),
            @ApiResponse(responseCode = "401", description = "Sin token"),
            @ApiResponse(responseCode = "403", description = "Solo para ADMIN"),
            @ApiResponse(responseCode = "404", description = "La reserva no existe"),
            @ApiResponse(responseCode = "409", description = "El estado actual no permite completar")
    })
    public ReservationResponse complete(@PathVariable UUID id) {
        return reservationService.complete(id);
    }
}
