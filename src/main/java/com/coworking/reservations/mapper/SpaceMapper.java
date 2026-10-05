package com.coworking.reservations.mapper;

import com.coworking.reservations.domain.entity.Space;
import com.coworking.reservations.dto.response.SpaceResponse;
import org.mapstruct.Mapper;

@Mapper(uses = UtcTime.class)
public interface SpaceMapper {

    SpaceResponse toResponse(Space space);
}
