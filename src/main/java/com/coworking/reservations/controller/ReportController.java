package com.coworking.reservations.controller;

import com.coworking.reservations.dto.response.OccupancyResponse;
import com.coworking.reservations.service.OccupancyReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reportes", description = "Reportes para administradores")
public class ReportController {

    private final OccupancyReportService occupancyReportService;

    public ReportController(OccupancyReportService occupancyReportService) {
        this.occupancyReportService = occupancyReportService;
    }

    @GetMapping("/occupancy")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Ocupacion por espacio en un rango de fechas",
            description = "Cuenta solo reservas CONFIRMED y COMPLETED, recortadas al rango. El resultado se guarda en cache y se limpia cuando una reserva cambia.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ocupacion de cada espacio"),
            @ApiResponse(responseCode = "400", description = "Rango invalido o mayor al maximo permitido"),
            @ApiResponse(responseCode = "401", description = "Sin token"),
            @ApiResponse(responseCode = "403", description = "Solo para ADMIN")
    })
    public List<OccupancyResponse> occupancy(
            @Parameter(description = "Inicio del rango, formato ISO 8601", example = "2030-06-01T00:00:00Z")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(description = "Fin del rango, formato ISO 8601", example = "2030-06-08T00:00:00Z")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return occupancyReportService.occupancy(from, to);
    }
}
