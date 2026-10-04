package com.coworking.reservations.scheduler;

import com.coworking.reservations.service.ReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationCompletionJob {

    private static final Logger log = LoggerFactory.getLogger(ReservationCompletionJob.class);

    private final ReservationService reservationService;

    public ReservationCompletionJob(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Scheduled(cron = "${app.reservation.completion-job-cron}")
    public void completeFinishedReservations() {
        int completed = reservationService.completeExpired();
        if (completed > 0) {
            log.info("{} reservas pasaron a completadas", completed);
        }
    }
}
