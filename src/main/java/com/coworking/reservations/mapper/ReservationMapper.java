package com.coworking.reservations.mapper;

import com.coworking.reservations.domain.entity.Reservation;
import com.coworking.reservations.dto.response.ReservationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(uses = UtcTime.class)
public interface ReservationMapper {

    @Mapping(source = "space.id", target = "spaceId")
    @Mapping(source = "space.name", target = "spaceName")
    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "user.email", target = "userEmail")
    ReservationResponse toResponse(Reservation reservation);
}
