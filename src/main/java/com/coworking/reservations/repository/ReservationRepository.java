package com.coworking.reservations.repository;

import com.coworking.reservations.domain.entity.Reservation;
import com.coworking.reservations.domain.enums.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID>, JpaSpecificationExecutor<Reservation> {

    // rango [inicio, fin): una que termina a las 11 no choca con otra que empieza a las 11
    @Query("""
            select case when count(r) > 0 then true else false end
            from Reservation r
            where r.space.id = :spaceId
              and r.status in :statuses
              and r.startTime < :end
              and r.endTime > :start
            """)
    boolean existsOverlapping(@Param("spaceId") UUID spaceId,
                              @Param("start") OffsetDateTime start,
                              @Param("end") OffsetDateTime end,
                              @Param("statuses") Collection<ReservationStatus> statuses);

    // el entity graph trae espacio y usuario en la misma consulta para no hacer una consulta extra por cada fila
    @Override
    @EntityGraph(attributePaths = {"space", "user"})
    Page<Reservation> findAll(Specification<Reservation> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"space", "user"})
    Optional<Reservation> findWithDetailsById(UUID id);
}
