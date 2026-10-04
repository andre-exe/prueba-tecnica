package com.coworking.reservations.repository;

import com.coworking.reservations.domain.entity.Space;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SpaceRepository extends JpaRepository<Space, UUID>, JpaSpecificationExecutor<Space> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);

    // bloquea la fila del espacio hasta que termine la transaccion, asi dos reservas del mismo espacio se atienden una a la vez
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Space s where s.id = :id")
    Optional<Space> findByIdForUpdate(@Param("id") UUID id);
}
