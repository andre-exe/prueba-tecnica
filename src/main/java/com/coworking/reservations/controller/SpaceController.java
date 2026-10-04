package com.coworking.reservations.controller;

import com.coworking.reservations.domain.enums.SpaceType;
import com.coworking.reservations.dto.request.SpaceFilter;
import com.coworking.reservations.dto.request.SpaceRequest;
import com.coworking.reservations.dto.response.PageResponse;
import com.coworking.reservations.dto.response.SpaceResponse;
import com.coworking.reservations.service.SpaceService;
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
public class SpaceController {

    private final SpaceService spaceService;

    public SpaceController(SpaceService spaceService) {
        this.spaceService = spaceService;
    }

    @GetMapping
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
    public SpaceResponse get(@PathVariable UUID id, Authentication authentication) {
        return spaceService.findById(id, isAdmin(authentication));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SpaceResponse> create(@Valid @RequestBody SpaceRequest request) {
        SpaceResponse created = spaceService.create(request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri()).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public SpaceResponse update(@PathVariable UUID id, @Valid @RequestBody SpaceRequest request) {
        return spaceService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        spaceService.deactivate(id);
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
