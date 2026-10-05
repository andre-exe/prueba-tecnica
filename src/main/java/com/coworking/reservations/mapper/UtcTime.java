package com.coworking.reservations.mapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

// los mapper lo usan solos para que toda fecha de una respuesta salga en utc, sin importar la zona del servidor
public final class UtcTime {

    private UtcTime() {
    }

    public static OffsetDateTime toUtc(OffsetDateTime value) {
        return value == null ? null : value.withOffsetSameInstant(ZoneOffset.UTC);
    }
}
