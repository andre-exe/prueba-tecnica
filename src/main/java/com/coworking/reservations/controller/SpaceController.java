package com.coworking.reservations.controller;

import com.coworking.reservations.domain.enums.SpaceType;
import com.coworking.reservations.dto.request.SpaceFilter;
import com.coworking.reservations.dto.request.SpaceRequest;
import com.coworking.reservations.dto.response.PageResponse;
import com.coworking.reservations.dto.response.SpaceResponse;
import com.coworking.reservations.service.SpaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/spaces")
@Tag(name = "Espacios", description = "Gestion de espacios de coworking")
public class SpaceController {

    private final SpaceService spaceService;

    public SpaceController(SpaceService spaceService) {
        this.spaceService = spaceService;
    }

    @GetMapping
    @Operation(summary = "Listar espacios",
            description = "Paginado y con filtros opcionales. Un usuario normal solo ve los espacios activos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina de espacios"),
            @ApiResponse(responseCode = "400", description = "Filtro, orden o paginacion invalidos"),
            @ApiResponse(responseCode = "401", description = "Sin token")
    })
    public PageResponse<SpaceResponse> list(@RequestParam(required = false) SpaceType type,
                                            @RequestParam(required = false) Integer minCapacity,
                                            @RequestParam(required = false) String location,
                                            @RequestParam(required = false) Boolean active,
                                            @PageableDefault(size = 20, sort = "name") Pageable pageable,
                                            Authentication authentication) {
        // un usuario normal solo ve los espacios activos, el admin puede filtrar como quiera
        Boolean effectiveActive = isAdmin(authentication) ? active : Boolean.TRUE;
        return spaceService.search(new SpaceFilter(type, minCapacity, location, effectiveActive), pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un espacio")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Espacio encontrado"),
            @ApiResponse(responseCode = "401", description = "Sin token"),
            @ApiResponse(responseCode = "404", description = "No existe, o esta inactivo y quien consulta no es ADMIN")
    })
    public SpaceResponse get(@PathVariable UUID id, Authentication authentication) {
        return spaceService.findById(id, isAdmin(authentication));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear un espacio", description = "Solo ADMIN")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Espacio creado"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos"),
            @ApiResponse(responseCode = "401", description = "Sin token"),
            @ApiResponse(responseCode = "403", description = "Solo para ADMIN"),
            @ApiResponse(responseCode = "409", description = "Ya existe un espacio con ese nombre")
    })
    public ResponseEntity<SpaceResponse> create(@Valid @RequestBody SpaceRequest request) {
        SpaceResponse created = spaceService.create(request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri()).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar un espacio", description = "Solo ADMIN")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Espacio actualizado"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos"),
            @ApiResponse(responseCode = "401", description = "Sin token"),
            @ApiResponse(responseCode = "403", description = "Solo para ADMIN"),
            @ApiResponse(responseCode = "404", description = "El espacio no existe"),
            @ApiResponse(responseCode = "409", description = "Nombre repetido o modificado por otra operacion")
    })
    public SpaceResponse update(@PathVariable UUID id, @Valid @RequestBody SpaceRequest request) {
        return spaceService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Desactivar un espacio",
            description = "Borrado logico: el espacio queda inactivo y se conservan las reservas historicas. Solo ADMIN")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Espacio desactivado"),
            @ApiResponse(responseCode = "401", description = "Sin token"),
            @ApiResponse(responseCode = "403", description = "Solo para ADMIN"),
            @ApiResponse(responseCode = "404", description = "El espacio no existe")
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        spaceService.deactivate(id);
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
