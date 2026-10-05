package com.coworking.reservations.scheduler;

import com.coworking.reservations.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationCompletionJobTest {

    @Mock
    private ReservationService reservationService;
    @InjectMocks
    private ReservationCompletionJob job;

    @Test
    void eachRunAsksTheServiceToCompleteTheFinishedReservations() {
        when(reservationService.completeExpired()).thenReturn(3);

        job.completeFinishedReservations();

        verify(reservationService).completeExpired();
    }

    @Test
    void aRunWithNothingToDoIsHarmless() {
        when(reservationService.completeExpired()).thenReturn(0);

        job.completeFinishedReservations();
        job.completeFinishedReservations();

        verify(reservationService, times(2)).completeExpired();
    }
}
