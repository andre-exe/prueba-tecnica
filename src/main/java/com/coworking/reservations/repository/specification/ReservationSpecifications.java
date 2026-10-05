package com.coworking.reservations.repository.specification;

import com.coworking.reservations.domain.entity.Reservation;
import com.coworking.reservations.domain.enums.ReservationStatus;
import com.coworking.reservations.dto.request.ReservationFilter;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public final class ReservationSpecifications {

    private ReservationSpecifications() {
    }

    public static Specification<Reservation> confirmedAndEndedBefore(OffsetDateTime now) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), ReservationStatus.CONFIRMED),
                cb.lessThan(root.get("endTime"), now));
    }

    public static Specification<Reservation> withFilters(ReservationFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.spaceId() != null) {
                predicates.add(cb.equal(root.get("space").get("id"), filter.spaceId()));
            }
            if (filter.userId() != null) {
                predicates.add(cb.equal(root.get("user").get("id"), filter.userId()));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startTime"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startTime"), filter.to()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
