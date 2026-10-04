package com.coworking.reservations.service;

import com.coworking.reservations.domain.entity.Space;
import com.coworking.reservations.domain.enums.SpaceType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class PricingServiceTest {

    private final PricingService pricingService = new PricingService();
    private final OffsetDateTime start = OffsetDateTime.of(2030, 6, 1, 10, 0, 0, 0, ZoneOffset.UTC);

    private Space spaceWithRate(String rate) {
        return new Space("Sala", SpaceType.MEETING_ROOM, 4, "Piso 1", new BigDecimal(rate));
    }

    @Test
    void chargesHourlyRateForWholeHours() {
        assertThat(pricingService.calculate(spaceWithRate("12.50"), start, start.plusHours(3))).isEqualByComparingTo("37.50");
    }

    @Test
    void chargesProportionallyForPartialHours() {
        assertThat(pricingService.calculate(spaceWithRate("10.00"), start, start.plusMinutes(90))).isEqualByComparingTo("15.00");
    }

    @Test
    void roundsToTwoDecimals() {
        assertThat(pricingService.calculate(spaceWithRate("10.00"), start, start.plusMinutes(35))).isEqualByComparingTo("5.83");
    }
}
