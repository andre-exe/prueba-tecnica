package com.coworking.reservations.service;

import com.coworking.reservations.client.PaymentGatewayClient;
import com.coworking.reservations.client.PaymentResult;
import com.coworking.reservations.client.dto.PaymentValidationRequest;
import com.coworking.reservations.domain.entity.Reservation;
import com.coworking.reservations.domain.enums.ReservationStatus;
import com.coworking.reservations.domain.event.ReservationConfirmedEvent;
import com.coworking.reservations.domain.event.ReservationPendingPaymentEvent;
import com.coworking.reservations.domain.event.ReservationSnapshot;
import com.coworking.reservations.dto.response.ConfirmReservationResponse;
import com.coworking.reservations.exception.InvalidReservationStateException;
import com.coworking.reservations.exception.ResourceNotFoundException;
import com.coworking.reservations.mapper.ReservationMapper;
import com.coworking.reservations.repository.ReservationRepository;
import com.coworking.reservations.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.UUID;

@Service
public class ReservationConfirmationServiceImpl implements ReservationConfirmationService {

    private static final String CURRENCY = "USD";

    private final ReservationRepository reservationRepository;
    private final ReservationMapper reservationMapper;
    private final PaymentGatewayClient paymentGateway;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate writeTransaction;

    public ReservationConfirmationServiceImpl(ReservationRepository reservationRepository,
                                              ReservationMapper reservationMapper,
                                              PaymentGatewayClient paymentGateway,
                                              ApplicationEventPublisher eventPublisher,
                                              PlatformTransactionManager transactionManager) {
        this.reservationRepository = reservationRepository;
        this.reservationMapper = reservationMapper;
        this.paymentGateway = paymentGateway;
        this.eventPublisher = eventPublisher;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.writeTransaction = new TransactionTemplate(transactionManager);
    }

    // este metodo no es transaccional a proposito: el pago es lento y no debe tener una conexion de base de datos agarrada
    @Override
    public ConfirmReservationResponse confirm(UUID id, CurrentUser requester) {
        PaymentValidationRequest paymentRequest = Objects.requireNonNull(
                readTransaction.execute(status -> prepare(id, requester)));

        PaymentResult result = paymentGateway.validate(paymentRequest);

        return Objects.requireNonNull(writeTransaction.execute(status -> apply(id, result)));
    }

    private PaymentValidationRequest prepare(UUID id, CurrentUser requester) {
        Reservation reservation = reservationRepository.findWithDetailsById(id)
                .filter(r -> requester.admin() || r.getUser().getId().equals(requester.id()))
                .orElseThrow(() -> notFound(id));

        if (!reservation.getStatus().state().allowedTransitions().contains(ReservationStatus.CONFIRMED)) {
            throw new InvalidReservationStateException(
                    "No se puede confirmar una reserva en estado " + reservation.getStatus());
        }
        return new PaymentValidationRequest(reservation.getId(), reservation.getTotalPrice(), CURRENCY,
                reservation.getPaymentMethod());
    }

    // se vuelve a leer la reserva porque pudo cambiar mientras se esperaba al proveedor, y el @Version protege ese cruce
    private ConfirmReservationResponse apply(UUID id, PaymentResult result) {
        Reservation reservation = reservationRepository.findWithDetailsById(id)
                .orElseThrow(() -> notFound(id));

        String message;
        if (result.isApproved()) {
            reservation.confirm();
            eventPublisher.publishEvent(new ReservationConfirmedEvent(ReservationSnapshot.of(reservation)));
            message = "Reserva confirmada";
        } else {
            boolean firstTime = reservation.getStatus() != ReservationStatus.PENDING_PAYMENT;
            reservation.markPendingPayment();
            if (firstTime) {
                eventPublisher.publishEvent(new ReservationPendingPaymentEvent(ReservationSnapshot.of(reservation)));
            }
            message = "El servicio de pago no esta disponible. La reserva quedo pendiente de pago, "
                    + "puedes reintentar la confirmacion mas tarde";
        }
        return new ConfirmReservationResponse(message,
                reservationMapper.toResponse(reservationRepository.saveAndFlush(reservation)));
    }

    private ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("Reserva no encontrada: " + id);
    }
}
