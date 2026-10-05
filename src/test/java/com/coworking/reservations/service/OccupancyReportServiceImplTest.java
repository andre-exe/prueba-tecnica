package com.coworking.reservations.service;

import com.coworking.reservations.config.properties.ReportCacheProperties;
import com.coworking.reservations.dto.response.OccupancyResponse;
import com.coworking.reservations.exception.InvalidDateRangeException;
import com.coworking.reservations.repository.ReservationRepository;
import com.coworking.reservations.repository.projection.OccupancyRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OccupancyReportServiceImplTest {

    private static final OffsetDateTime FROM = OffsetDateTime.of(2030, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private ReservationRepository reservationRepository;

    private OccupancyReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OccupancyReportServiceImpl(reservationRepository,
                new ReportCacheProperties(Duration.ofMinutes(10), 100, 366));
    }

    private OccupancyRow row(String name, String reservedHours) {
        UUID id = UUID.randomUUID();
        return new OccupancyRow() {
            @Override
            public UUID getSpaceId() {
                return id;
            }

            @Override
            public String getSpaceName() {
                return name;
            }

            @Override
            public BigDecimal getReservedHours() {
                return new BigDecimal(reservedHours);
            }
        };
    }

    @Test
    void calculatesThePercentageWithTwoDecimals() {
        when(reservationRepository.findOccupancy(any(), any())).thenReturn(List.of(row("Sala A", "4.0000")));

        List<OccupancyResponse> report = service.occupancy(FROM, FROM.plusDays(1));

        assertThat(report).hasSize(1);
        assertThat(report.get(0).spaceName()).isEqualTo("Sala A");
        assertThat(report.get(0).reservedHours()).isEqualByComparingTo("4.00");
        assertThat(report.get(0).availableHours()).isEqualByComparingTo("24.00");
        assertThat(report.get(0).occupancyPercentage()).isEqualByComparingTo("16.67");
    }

    @Test
    void aSpaceWithoutReservationsIsAtZeroPercent() {
        when(reservationRepository.findOccupancy(any(), any())).thenReturn(List.of(row("Sala vacia", "0")));

        OccupancyResponse response = service.occupancy(FROM, FROM.plusDays(7)).get(0);

        assertThat(response.occupancyPercentage()).isEqualByComparingTo("0.00");
        assertThat(response.availableHours()).isEqualByComparingTo("168.00");
    }

    @Test
    void aFullyBookedSpaceIsAtOneHundredPercent() {
        when(reservationRepository.findOccupancy(any(), any())).thenReturn(List.of(row("Sala llena", "24")));

        assertThat(service.occupancy(FROM, FROM.plusDays(1)).get(0).occupancyPercentage()).isEqualByComparingTo("100.00");
    }

    @Test
    void rejectsARangeWhereFromIsNotBeforeTo() {
        assertThatThrownBy(() -> service.occupancy(FROM, FROM)).isInstanceOf(InvalidDateRangeException.class);
        assertThatThrownBy(() -> service.occupancy(FROM, FROM.minusDays(1))).isInstanceOf(InvalidDateRangeException.class);
        verifyNoInteractions(reservationRepository);
    }

    @Test
    void rejectsARangeLongerThanTheConfiguredMaximum() {
        assertThatThrownBy(() -> service.occupancy(FROM, FROM.plusDays(367)))
                .isInstanceOf(InvalidDateRangeException.class)
                .hasMessageContaining("366");
        verifyNoInteractions(reservationRepository);
    }

    @Test
    void acceptsARangeOfExactlyTheMaximum() {
        when(reservationRepository.findOccupancy(any(), any())).thenReturn(List.of());

        assertThat(service.occupancy(FROM, FROM.plusDays(366))).isEmpty();
    }
}
