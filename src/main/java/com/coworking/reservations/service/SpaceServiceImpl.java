package com.coworking.reservations.service;

import com.coworking.reservations.domain.entity.Space;
import com.coworking.reservations.dto.request.SpaceFilter;
import com.coworking.reservations.dto.request.SpaceRequest;
import com.coworking.reservations.dto.response.PageResponse;
import com.coworking.reservations.dto.response.SpaceResponse;
import com.coworking.reservations.exception.DuplicateSpaceNameException;
import com.coworking.reservations.exception.ResourceNotFoundException;
import com.coworking.reservations.mapper.SpaceMapper;
import com.coworking.reservations.repository.SpaceRepository;
import com.coworking.reservations.repository.specification.SpaceSpecifications;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SpaceServiceImpl implements SpaceService {

    private final SpaceRepository spaceRepository;
    private final SpaceMapper spaceMapper;

    public SpaceServiceImpl(SpaceRepository spaceRepository, SpaceMapper spaceMapper) {
        this.spaceRepository = spaceRepository;
        this.spaceMapper = spaceMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SpaceResponse> search(SpaceFilter filter, Pageable pageable) {
        return PageResponse.from(
                spaceRepository.findAll(SpaceSpecifications.withFilters(filter), pageable)
                        .map(spaceMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public SpaceResponse findById(UUID id, boolean includeInactive) {
        Space space = spaceRepository.findById(id)
                .filter(s -> includeInactive || s.isActive())
                .orElseThrow(() -> notFound(id));
        return spaceMapper.toResponse(space);
    }

    @Override
    @Transactional
    public SpaceResponse create(SpaceRequest request) {
        String name = request.name().trim();
        if (spaceRepository.existsByName(name)) {
            throw new DuplicateSpaceNameException(name);
        }
        Space space = new Space(name, request.type(), request.capacity(), request.location().trim(), request.hourlyRate());
        return spaceMapper.toResponse(spaceRepository.saveAndFlush(space));
    }

    @Override
    @Transactional
    public SpaceResponse update(UUID id, SpaceRequest request) {
        Space space = spaceRepository.findById(id).orElseThrow(() -> notFound(id));
        String name = request.name().trim();
        if (spaceRepository.existsByNameAndIdNot(name, id)) {
            throw new DuplicateSpaceNameException(name);
        }
        space.update(name, request.type(), request.capacity(), request.location().trim(), request.hourlyRate());
        return spaceMapper.toResponse(spaceRepository.saveAndFlush(space));
    }

    @Override
    @Transactional
    public void deactivate(UUID id) {
        // borrado logico: las reservas viejas siguen apuntando a este espacio
        spaceRepository.findById(id).orElseThrow(() -> notFound(id)).deactivate();
    }

    private ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("Espacio no encontrado: " + id);
    }
}
