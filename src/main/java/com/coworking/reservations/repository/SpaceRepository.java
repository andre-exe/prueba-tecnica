package com.coworking.reservations.repository;

import com.coworking.reservations.domain.entity.Space;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface SpaceRepository extends JpaRepository<Space, UUID>, JpaSpecificationExecutor<Space> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);
}
