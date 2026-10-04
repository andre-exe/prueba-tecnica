package com.coworking.reservations.service;

import com.coworking.reservations.domain.entity.Reservation;
import com.coworking.reservations.domain.entity.Space;
import com.coworking.reservations.domain.entity.User;
import com.coworking.reservations.domain.enums.ReservationStatus;
import com.coworking.reservations.dto.request.CreateReservationRequest;
import com.coworking.reservations.dto.request.ReservationFilter;
import com.coworking.reservations.dto.response.PageResponse;
import com.coworking.reservations.dto.response.ReservationResponse;
import com.coworking.reservations.exception.OverlappingReservationException;
import com.coworking.reservations.exception.ResourceNotFoundException;
import com.coworking.reservations.mapper.ReservationMapper;
import com.coworking.reservations.repository.ReservationRepository;
import com.coworking.reservations.repository.SpaceRepository;
import com.coworking.reservations.repository.UserRepository;
import com.coworking.reservations.repository.specification.ReservationSpecifications;
import com.coworking.reservations.security.CurrentUser;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ReservationServiceImpl implements ReservationService {

    // los unicos estados que ocupan el espacio, igual que en el constraint de la base
    private static final Set<ReservationStatus> ACTIVE_STATUSES =
            EnumSet.of(ReservationStatus.PENDING, ReservationStatus.PENDING_PAYMENT, ReservationStatus.CONFIRMED);

    private final ReservationRepository reservationRepository;
    private final SpaceRepository spaceRepository;
    private final UserRepository userRepository;
    private final ReservationValidator validator;
    private final PricingService pricingService;
    private final ReservationMapper reservationMapper;
    private final Clock clock;

    public ReservationServiceImpl(ReservationRepository reservationRepository, SpaceRepository spaceRepository,
                                  UserRepository userRepository, ReservationValidator validator,
                                  PricingService pricingService, ReservationMapper reservationMapper, Clock clock) {
        this.reservationRepository = reservationRepository;
        this.spaceRepository = spaceRepository;
        this.userRepository = userRepository;
        this.validator = validator;
        this.pricingService = pricingService;
        this.reservationMapper = reservationMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ReservationResponse create(UUID userId, CreateReservationRequest request) {
        // el lock deja pasar de a una las reservas del mismo espacio, la segunda espera y ya ve la primera guardada
        Space space = spaceRepository.findByIdForUpdate(request.spaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Espacio no encontrado: " + request.spaceId()));

        validator.validate(request, space, OffsetDateTime.now(clock));

        if (reservationRepository.existsOverlapping(space.getId(), request.startTime(), request.endTime(), ACTIVE_STATUSES)) {
            throw new OverlappingReservationException();
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        Reservation reservation = new Reservation(user, space, request.startTime(), request.endTime(),
                request.attendees(), pricingService.calculate(space, request.startTime(), request.endTime()),
                request.paymentMethod().trim());

        // saveAndFlush para que si la base rechaza el solape el error salga aqui y no al cerrar la transaccion
        return reservationMapper.toResponse(reservationRepository.saveAndFlush(reservation));
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationResponse findById(UUID id, CurrentUser requester) {
        return reservationMapper.toResponse(findVisible(id, requester));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReservationResponse> search(ReservationFilter filter, Pageable pageable, CurrentUser requester) {
        UUID userId = requester.admin() ? filter.userId() : requester.id();
        ReservationFilter effective = new ReservationFilter(filter.status(), filter.spaceId(), userId, filter.from(), filter.to());
        return PageResponse.from(
                reservationRepository.findAll(ReservationSpecifications.withFilters(effective), pageable)
                        .map(reservationMapper::toResponse));
    }

    @Override
    @Transactional
    public ReservationResponse cancel(UUID id, CurrentUser requester) {
        Reservation reservation = findVisible(id, requester);
        reservation.cancel(OffsetDateTime.now(clock));
        return reservationMapper.toResponse(reservationRepository.saveAndFlush(reservation));
    }

    @Override
    @Transactional
    public ReservationResponse complete(UUID id) {
        Reservation reservation = reservationRepository.findWithDetailsById(id)
                .orElseThrow(() -> notFound(id));
        reservation.complete(OffsetDateTime.now(clock));
        return reservationMapper.toResponse(reservationRepository.saveAndFlush(reservation));
    }

    @Override
    @Transactional
    public int completeExpired() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Reservation> expired = reservationRepository.findAll(ReservationSpecifications.confirmedAndEndedBefore(now));
        expired.forEach(reservation -> reservation.complete(now));
        return expired.size();
    }

    // si no es suya responde 404 igual que si no existiera, para no revelar que esa reserva existe
    private Reservation findVisible(UUID id, CurrentUser requester) {
        return reservationRepository.findWithDetailsById(id)
                .filter(r -> requester.admin() || r.getUser().getId().equals(requester.id()))
                .orElseThrow(() -> notFound(id));
    }

    private ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("Reserva no encontrada: " + id);
    }
}
