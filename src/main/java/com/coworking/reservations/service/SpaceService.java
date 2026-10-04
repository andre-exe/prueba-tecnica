package com.coworking.reservations.service;

import com.coworking.reservations.dto.request.SpaceFilter;
import com.coworking.reservations.dto.request.SpaceRequest;
import com.coworking.reservations.dto.response.PageResponse;
import com.coworking.reservations.dto.response.SpaceResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SpaceService {

    PageResponse<SpaceResponse> search(SpaceFilter filter, Pageable pageable);

    SpaceResponse findById(UUID id, boolean includeInactive);

    SpaceResponse create(SpaceRequest request);

    SpaceResponse update(UUID id, SpaceRequest request);

    void deactivate(UUID id);
}
