package com.coworking.reservations.dto.request;

import com.coworking.reservations.domain.enums.SpaceType;

public record SpaceFilter(
        SpaceType type,
        Integer minCapacity,
        String location,
        Boolean active) {
}
