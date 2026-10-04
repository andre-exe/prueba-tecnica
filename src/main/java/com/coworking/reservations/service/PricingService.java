package com.coworking.reservations.service;

import com.coworking.reservations.domain.entity.Space;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;

@Service
public class PricingService {

    private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);

    public BigDecimal calculate(Space space, OffsetDateTime start, OffsetDateTime end) {
        long minutes = Duration.between(start, end).toMinutes();
        return space.getHourlyRate()
                .multiply(BigDecimal.valueOf(minutes))
                .divide(MINUTES_PER_HOUR, 2, RoundingMode.HALF_UP);
    }
}
