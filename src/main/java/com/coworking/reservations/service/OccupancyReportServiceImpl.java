package com.coworking.reservations.service;

import com.coworking.reservations.config.properties.ReportCacheProperties;
import com.coworking.reservations.dto.response.OccupancyResponse;
import com.coworking.reservations.exception.InvalidDateRangeException;
import com.coworking.reservations.repository.ReservationRepository;
import com.coworking.reservations.repository.projection.OccupancyRow;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class OccupancyReportServiceImpl implements OccupancyReportService {

    private static final BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf(3600);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ReservationRepository reservationRepository;
    private final ReportCacheProperties properties;

    public OccupancyReportServiceImpl(ReservationRepository reservationRepository, ReportCacheProperties properties) {
        this.reservationRepository = reservationRepository;
        this.properties = properties;
    }

    // la llave usa el instante y no el texto, asi "10:00Z" y "10:00+00:00" caen en la misma entrada
    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "occupancyReport", key = "#from.toInstant().toString() + '_' + #to.toInstant().toString()")
    public List<OccupancyResponse> occupancy(OffsetDateTime from, OffsetDateTime to) {
        validateRange(from, to);

        // por ahora las horas disponibles son todas las horas del rango, el horario laboral queda para despues
        BigDecimal availableHours = BigDecimal.valueOf(Duration.between(from, to).toSeconds())
                .divide(SECONDS_PER_HOUR, 2, RoundingMode.HALF_UP);

        return reservationRepository.findOccupancy(from, to).stream()
                .map(row -> toResponse(row, availableHours))
                .toList();
    }

    private OccupancyResponse toResponse(OccupancyRow row, BigDecimal availableHours) {
        BigDecimal reserved = row.getReservedHours().setScale(2, RoundingMode.HALF_UP);
        BigDecimal percentage = reserved.multiply(HUNDRED).divide(availableHours, 2, RoundingMode.HALF_UP);
        return new OccupancyResponse(row.getSpaceId(), row.getSpaceName(), reserved, availableHours, percentage);
    }

    private void validateRange(OffsetDateTime from, OffsetDateTime to) {
        if (!from.isBefore(to)) {
            throw new InvalidDateRangeException("'from' debe ser anterior a 'to'");
        }
        if (Duration.between(from, to).toDays() > properties.maxRangeDays()) {
            throw new InvalidDateRangeException("El rango maximo es de " + properties.maxRangeDays() + " dias");
        }
    }
}
