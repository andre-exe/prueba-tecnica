package com.coworking.reservations.service;

import com.coworking.reservations.client.PaymentGatewayClient;
import com.coworking.reservations.client.PaymentResult;
import com.coworking.reservations.client.dto.PaymentValidationRequest;
import com.coworking.reservations.domain.entity.Reservation;
import com.coworking.reservations.domain.entity.Space;
import com.coworking.reservations.domain.entity.User;
import com.coworking.reservations.domain.enums.ReservationStatus;
import com.coworking.reservations.domain.enums.Role;
import com.coworking.reservations.domain.enums.SpaceType;
import com.coworking.reservations.domain.event.ReservationConfirmedEvent;
import com.coworking.reservations.domain.event.ReservationPendingPaymentEvent;
import com.coworking.reservations.dto.response.ConfirmReservationResponse;
import com.coworking.reservations.exception.InvalidReservationStateException;
import com.coworking.reservations.exception.PaymentDeclinedException;
import com.coworking.reservations.exception.ResourceNotFoundException;
import com.coworking.reservations.mapper.ReservationMapper;
import com.coworking.reservations.repository.ReservationRepository;
import com.coworking.reservations.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationConfirmationServiceImplTest {

    private static final OffsetDateTime START = OffsetDateTime.of(2030, 6, 1, 10, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private PaymentGatewayClient paymentGateway;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private PlatformTransactionManager transactionManager;

    private ReservationConfirmationServiceImpl service;
    private final UUID reservationId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private Reservation reservation;

    @BeforeEach
    void setUp() {
        service = new ReservationConfirmationServiceImpl(reservationRepository, Mappers.getMapper(ReservationMapper.class),
                paymentGateway, eventPublisher, transactionManager);

        User owner = new User("ana@test.com", "hash", "Ana", Role.USER);
        ReflectionTestUtils.setField(owner, "id", ownerId);
        Space space = new Space("Sala Roble", SpaceType.MEETING_ROOM, 6, "Piso 2", BigDecimal.TEN);
        reservation = new Reservation(owner, space, START, START.plusHours(1), 2, new BigDecimal("40.00"), "tok_ok");
        ReflectionTestUtils.setField(reservation, "id", reservationId);
    }

    private void reservationExists() {
        when(reservationRepository.findWithDetailsById(reservationId)).thenReturn(Optional.of(reservation));
    }

    private void saveReturnsSameEntity() {
        when(reservationRepository.saveAndFlush(reservation)).thenReturn(reservation);
    }

    @Test
    void approvedPaymentConfirmsAndPublishesEvent() {
        reservationExists();
        saveReturnsSameEntity();
        when(paymentGateway.validate(any())).thenReturn(PaymentResult.approved("txn-1"));

        ConfirmReservationResponse response = service.confirm(reservationId, new CurrentUser(ownerId, false));

        assertThat(response.reservation().status()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(eventPublisher).publishEvent(any(ReservationConfirmedEvent.class));
    }

    @Test
    void paymentRequestCarriesTheReservationData() {
        reservationExists();
        saveReturnsSameEntity();
        when(paymentGateway.validate(any())).thenReturn(PaymentResult.approved("txn-1"));

        service.confirm(reservationId, new CurrentUser(ownerId, false));

        ArgumentCaptor<PaymentValidationRequest> captor = ArgumentCaptor.forClass(PaymentValidationRequest.class);
        verify(paymentGateway).validate(captor.capture());
        assertThat(captor.getValue().reservationId()).isEqualTo(reservationId);
        assertThat(captor.getValue().amount()).isEqualByComparingTo("40.00");
        assertThat(captor.getValue().currency()).isEqualTo("USD");
        assertThat(captor.getValue().paymentMethod()).isEqualTo("tok_ok");
    }

    @Test
    void declinedPaymentLeavesTheReservationPending() {
        reservationExists();
        when(paymentGateway.validate(any())).thenThrow(new PaymentDeclinedException("INSUFFICIENT_FUNDS"));

        assertThatThrownBy(() -> service.confirm(reservationId, new CurrentUser(ownerId, false)))
                .isInstanceOf(PaymentDeclinedException.class);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        verify(reservationRepository, never()).saveAndFlush(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void unavailableProviderMovesTheReservationToPendingPayment() {
        reservationExists();
        saveReturnsSameEntity();
        when(paymentGateway.validate(any())).thenReturn(PaymentResult.unavailable());

        ConfirmReservationResponse response = service.confirm(reservationId, new CurrentUser(ownerId, false));

        assertThat(response.reservation().status()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(response.message()).contains("reintentar");
        verify(eventPublisher).publishEvent(any(ReservationPendingPaymentEvent.class));
    }

    @Test
    void retryWhileProviderIsStillDownDoesNotRepeatTheNotification() {
        ReflectionTestUtils.setField(reservation, "status", ReservationStatus.PENDING_PAYMENT);
        reservationExists();
        saveReturnsSameEntity();
        when(paymentGateway.validate(any())).thenReturn(PaymentResult.unavailable());

        ConfirmReservationResponse response = service.confirm(reservationId, new CurrentUser(ownerId, false));

        assertThat(response.reservation().status()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void retryAfterRecoveryConfirmsAPendingPaymentReservation() {
        ReflectionTestUtils.setField(reservation, "status", ReservationStatus.PENDING_PAYMENT);
        reservationExists();
        saveReturnsSameEntity();
        when(paymentGateway.validate(any())).thenReturn(PaymentResult.approved("txn-2"));

        assertThat(service.confirm(reservationId, new CurrentUser(ownerId, false)).reservation().status())
                .isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void anotherUserGetsNotFoundAndPaymentIsNeverCalled() {
        reservationExists();

        assertThatThrownBy(() -> service.confirm(reservationId, new CurrentUser(UUID.randomUUID(), false)))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(paymentGateway);
    }

    @Test
    void adminCanConfirmAnyReservation() {
        reservationExists();
        saveReturnsSameEntity();
        when(paymentGateway.validate(any())).thenReturn(PaymentResult.approved("txn-3"));

        assertThat(service.confirm(reservationId, new CurrentUser(UUID.randomUUID(), true)).reservation().status())
                .isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void cancelledReservationIsRejectedBeforeCallingThePaymentProvider() {
        ReflectionTestUtils.setField(reservation, "status", ReservationStatus.CANCELLED);
        reservationExists();

        assertThatThrownBy(() -> service.confirm(reservationId, new CurrentUser(ownerId, false)))
                .isInstanceOf(InvalidReservationStateException.class);
        verifyNoInteractions(paymentGateway);
    }

    @Test
    void alreadyConfirmedReservationCannotBeConfirmedAgain() {
        ReflectionTestUtils.setField(reservation, "status", ReservationStatus.CONFIRMED);
        reservationExists();

        assertThatThrownBy(() -> service.confirm(reservationId, new CurrentUser(ownerId, false)))
                .isInstanceOf(InvalidReservationStateException.class);
        verifyNoInteractions(paymentGateway);
    }

    @Test
    void reservationCancelledWhileWaitingForPaymentCannotBeConfirmed() {
        // la primera lectura la ve pendiente, pero cuando vuelve del pago alguien ya la cancelo
        Reservation cancelledMeanwhile = new Reservation(reservation.getUser(), reservation.getSpace(), START,
                START.plusHours(1), 2, new BigDecimal("40.00"), "tok_ok");
        ReflectionTestUtils.setField(cancelledMeanwhile, "id", reservationId);
        ReflectionTestUtils.setField(cancelledMeanwhile, "status", ReservationStatus.CANCELLED);
        when(reservationRepository.findWithDetailsById(reservationId))
                .thenReturn(Optional.of(reservation), Optional.of(cancelledMeanwhile));
        when(paymentGateway.validate(any())).thenReturn(PaymentResult.approved("txn-4"));

        assertThatThrownBy(() -> service.confirm(reservationId, new CurrentUser(ownerId, false)))
                .isInstanceOf(InvalidReservationStateException.class);
        verifyNoInteractions(eventPublisher);
    }
}
