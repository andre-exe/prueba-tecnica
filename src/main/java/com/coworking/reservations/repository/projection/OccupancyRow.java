package com.coworking.reservations.repository.projection;

import java.math.BigDecimal;
import java.util.UUID;

public interface OccupancyRow {

    UUID getSpaceId();

    String getSpaceName();

    BigDecimal getReservedHours();
}
