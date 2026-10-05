package com.coworking.reservations.mapper;

import com.coworking.reservations.domain.entity.Reservation;
import com.coworking.reservations.domain.entity.Space;
import com.coworking.reservations.domain.entity.User;
import com.coworking.reservations.domain.enums.Role;
import com.coworking.reservations.domain.enums.SpaceType;
import com.coworking.reservations.dto.response.ReservationResponse;
import com.coworking.reservations.dto.response.SpaceResponse;
import com.coworking.reservations.dto.response.UserResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class UtcDatesMapperTest {

    private static final ZoneOffset EL_SALVADOR = ZoneOffset.ofHours(-6);

    @Test
    void reservationDatesAreAlwaysReturnedInUtc() {
        OffsetDateTime start = OffsetDateTime.of(2030, 6, 1, 10, 0, 0, 0, EL_SALVADOR);
        User user = new User("ana@test.com", "hash", "Ana", Role.USER);
        Space space = new Space("Sala", SpaceType.MEETING_ROOM, 4, "Piso 1", BigDecimal.TEN);
        Reservation reservation = new Reservation(user, space, start, start.plusHours(1), 2, BigDecimal.TEN, "tok_ok");
        ReflectionTestUtils.setField(reservation, "createdAt", start.minusDays(1));
        ReflectionTestUtils.setField(reservation, "updatedAt", start.minusHours(1));

        ReservationResponse response = Mappers.getMapper(ReservationMapper.class).toResponse(reservation);

        assertThat(response.startTime().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(response.startTime()).isEqualTo(start);
        assertThat(response.startTime().getHour()).isEqualTo(16);
        assertThat(response.endTime().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(response.createdAt().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(response.updatedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void spaceAndUserDatesAreReturnedInUtc() {
        OffsetDateTime local = OffsetDateTime.of(2030, 6, 1, 10, 0, 0, 0, EL_SALVADOR);
        Space space = new Space("Sala", SpaceType.HOT_DESK, 1, "Piso 1", BigDecimal.ONE);
        ReflectionTestUtils.setField(space, "createdAt", local);
        ReflectionTestUtils.setField(space, "updatedAt", local);
        User user = new User("ana@test.com", "hash", "Ana", Role.USER);
        ReflectionTestUtils.setField(user, "createdAt", local);

        SpaceResponse spaceResponse = Mappers.getMapper(SpaceMapper.class).toResponse(space);
        UserResponse userResponse = Mappers.getMapper(UserMapper.class).toResponse(user);

        assertThat(spaceResponse.createdAt().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(spaceResponse.updatedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(userResponse.createdAt().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(userResponse.createdAt()).isEqualTo(local);
    }

    @Test
    void nullDatesStayNull() {
        Space space = new Space("Sala", SpaceType.HOT_DESK, 1, "Piso 1", BigDecimal.ONE);

        SpaceResponse response = Mappers.getMapper(SpaceMapper.class).toResponse(space);

        assertThat(response.createdAt()).isNull();
    }
}
