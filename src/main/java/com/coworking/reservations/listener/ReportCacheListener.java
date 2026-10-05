package com.coworking.reservations.listener;

import com.coworking.reservations.domain.event.ReservationCancelledEvent;
import com.coworking.reservations.domain.event.ReservationCompletedEvent;
import com.coworking.reservations.domain.event.ReservationConfirmedEvent;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ReportCacheListener {

    // se limpia despues del commit, si fuera antes otra consulta podria volver a guardar datos viejos en el cache
    @TransactionalEventListener(
            classes = {ReservationConfirmedEvent.class, ReservationCancelledEvent.class, ReservationCompletedEvent.class},
            phase = TransactionPhase.AFTER_COMMIT)
    @CacheEvict(cacheNames = "occupancyReport", allEntries = true)
    public void evictOccupancyReport() {
    }
}
