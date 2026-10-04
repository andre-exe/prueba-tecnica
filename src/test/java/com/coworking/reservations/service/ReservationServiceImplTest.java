package com.coworking.reservations.service;

import com.coworking.reservations.config.properties.ReservationProperties;
import com.coworking.reservations.domain.entity.Reservation;
import com.coworking.reservations.domain.entity.Space;
import com.coworking.reservations.domain.entity.User;
import com.coworking.reservations.domain.enums.ReservationStatus;
import com.coworking.reservations.domain.enums.Role;
import com.coworking.reservations.domain.enums.SpaceType;
import com.coworking.reservations.dto.request.CreateReservationRequest;
import com.coworking.reservations.dto.response.ReservationResponse;
import com.coworking.reservations.exception.InvalidReservationRequestException;
import com.coworking.reservations.exception.OverlappingReservationException;
import com.coworking.reservations.exception.ResourceNotFoundException;
import com.coworking.reservations.mapper.ReservationMapper;
import com.coworking.reservations.repository.ReservationRepository;
import com.coworking.reservations.repository.SpaceRepository;
import com.coworking.reservations.repository.UserRepository;
import com.coworking.reservations.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceImplTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2030, 6, 1, 12, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private SpaceRepository spaceRepository;
    @Mock
    private UserRepository userRepository;

    private ReservationServiceImpl service;
    private Space space;
    private User user;
    private final UUID userId = UUID.randomUUID();
    private final UUID spaceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReservationProperties properties = new ReservationProperties(Duration.ofMinutes(30), Duration.ofHours(8), 90, "0 */5 * * * *");
        Clock clock = Clock.fixed(Instant.from(NOW), ZoneOffset.UTC);
        service = new ReservationServiceImpl(reservationRepository, spaceRepository, userRepository,
                new ReservationValidator(properties), new PricingService(),
                Mappers.getMapper(ReservationMapper.class), clock);

        space = new Space("Sala Roble", SpaceType.MEETING_ROOM, 6, "Piso 2", new BigDecimal("10.00"));
        ReflectionTestUtils.setField(space, "id", spaceId);
        user = new User("ana@test.com", "hash", "Ana", Role.USER);
        ReflectionTestUtils.setField(user, "id", userId);
    }

    private CreateReservationRequest request(OffsetDateTime start, OffsetDateTime end, int attendees) {
        return new CreateReservationRequest(spaceId, start, end, attendees, "tok_ok");
    }

    private void spaceFound() {
        when(spaceRepository.findByIdForUpdate(spaceId)).thenReturn(Optional.of(space));
    }

    @Test
    void createsPendingReservationWithCalculatedPrice() {
        spaceFound();
        when(reservationRepository.existsOverlapping(any(), any(), any(), any())).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(reservationRepository.saveAndFlush(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

        ReservationResponse response = service.create(userId, request(NOW.plusDays(1), NOW.plusDays(1).plusMinutes(90), 4));

        assertThat(response.status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(response.totalPrice()).isEqualByComparingTo("15.00");
        assertThat(response.userEmail()).isEqualTo("ana@test.com");
        assertThat(response.spaceName()).isEqualTo("Sala Roble");
    }

    @Test
    void rejectsOverlappingReservation() {
        spaceFound();
        when(reservationRepository.existsOverlapping(any(), any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.create(userId, request(NOW.plusDays(1), NOW.plusDays(1).plusHours(1), 2)))
                .isInstanceOf(OverlappingReservationException.class);
        verify(reservationRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsUnknownSpace() {
        when(spaceRepository.findByIdForUpdate(spaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(userId, request(NOW.plusDays(1), NOW.plusDays(1).plusHours(1), 2)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsInactiveSpace() {
        space.deactivate();
        spaceFound();

        assertThatThrownBy(() -> service.create(userId, request(NOW.plusDays(1), NOW.plusDays(1).plusHours(1), 2)))
                .isInstanceOf(InvalidReservationRequestException.class)
                .hasMessageContaining("no esta disponible");
    }

    @Test
    void rejectsMoreAttendeesThanCapacity() {
        spaceFound();

        assertThatThrownBy(() -> service.create(userId, request(NOW.plusDays(1), NOW.plusDays(1).plusHours(1), 7)))
                .isInstanceOf(InvalidReservationRequestException.class)
                .hasMessageContaining("capacidad");
    }

    @Test
    void rejectsEndBeforeStart() {
        spaceFound();

        assertThatThrownBy(() -> service.create(userId, request(NOW.plusDays(1).plusHours(2), NOW.plusDays(1), 2)))
                .isInstanceOf(InvalidReservationRequestException.class);
    }

    @Test
    void rejectsStartInThePast() {
        spaceFound();

        assertThatThrownBy(() -> service.create(userId, request(NOW.minusHours(1), NOW.plusHours(1), 2)))
                .isInstanceOf(InvalidReservationRequestException.class)
                .hasMessageContaining("futuro");
    }

    @Test
    void rejectsTooShortReservation() {
        spaceFound();

        assertThatThrownBy(() -> service.create(userId, request(NOW.plusDays(1), NOW.plusDays(1).plusMinutes(10), 2)))
                .isInstanceOf(InvalidReservationRequestException.class)
                .hasMessageContaining("minima");
    }

    @Test
    void rejectsTooLongReservation() {
        spaceFound();

        assertThatThrownBy(() -> service.create(userId, request(NOW.plusDays(1), NOW.plusDays(1).plusHours(9), 2)))
                .isInstanceOf(InvalidReservationRequestException.class)
                .hasMessageContaining("maxima");
    }

    @Test
    void rejectsReservationTooFarInAdvance() {
        spaceFound();

        assertThatThrownBy(() -> service.create(userId, request(NOW.plusDays(120), NOW.plusDays(120).plusHours(1), 2)))
                .isInstanceOf(InvalidReservationRequestException.class)
                .hasMessageContaining("anticipacion");
    }

    @Test
    void ownerCanReadTheirReservation() {
        UUID id = UUID.randomUUID();
        when(reservationRepository.findWithDetailsById(id)).thenReturn(Optional.of(reservationOf(user)));

        assertThat(service.findById(id, new CurrentUser(userId, false)).userId()).isEqualTo(userId);
    }

    @Test
    void anotherUserGetsNotFound() {
        UUID id = UUID.randomUUID();
        when(reservationRepository.findWithDetailsById(id)).thenReturn(Optional.of(reservationOf(user)));

        assertThatThrownBy(() -> service.findById(id, new CurrentUser(UUID.randomUUID(), false)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void adminCanReadAnyReservation() {
        UUID id = UUID.randomUUID();
        when(reservationRepository.findWithDetailsById(id)).thenReturn(Optional.of(reservationOf(user)));

        assertThat(service.findById(id, new CurrentUser(UUID.randomUUID(), true)).userId()).isEqualTo(userId);
    }

    private Reservation reservationOf(User owner) {
        return new Reservation(owner, space, NOW.plusDays(1), NOW.plusDays(1).plusHours(1), 2, BigDecimal.TEN, "tok_ok");
    }
}
