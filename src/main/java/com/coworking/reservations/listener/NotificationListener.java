package com.coworking.reservations.listener;

import com.coworking.reservations.config.properties.NotificationProperties;
import com.coworking.reservations.domain.event.ReservationCancelledEvent;
import com.coworking.reservations.domain.event.ReservationConfirmedEvent;
import com.coworking.reservations.domain.event.ReservationPendingPaymentEvent;
import com.coworking.reservations.domain.event.ReservationSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private final NotificationProperties properties;

    public NotificationListener(NotificationProperties properties) {
        this.properties = properties;
    }

    // despues del commit para no avisar de algo que luego se revierte, y en otro hilo para no frenar la respuesta http
    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConfirmed(ReservationConfirmedEvent event) {
        sendEmail("Reserva confirmada", event.reservation());
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPendingPayment(ReservationPendingPaymentEvent event) {
        sendEmail("Tu reserva esta pendiente de pago", event.reservation());
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCancelled(ReservationCancelledEvent event) {
        sendEmail("Reserva cancelada", event.reservation());
    }

    private void sendEmail(String subject, ReservationSnapshot reservation) {
        simulateSmtpLatency();
        log.info("correo enviado from={} to={} subject='{}' reserva={} espacio='{}' inicio={} fin={} monto={}",
                properties.from(), reservation.userEmail(), subject, reservation.reservationId(),
                reservation.spaceName(), reservation.startTime(), reservation.endTime(), reservation.totalPrice());
    }

    private void simulateSmtpLatency() {
        try {
            Thread.sleep(properties.simulatedDelay());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
