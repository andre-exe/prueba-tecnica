package com.coworking.reservations.repository.specification;

import com.coworking.reservations.domain.entity.Space;
import com.coworking.reservations.dto.request.SpaceFilter;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SpaceSpecifications {

    private SpaceSpecifications() {
    }

    public static Specification<Space> withFilters(SpaceFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.type() != null) {
                predicates.add(cb.equal(root.get("type"), filter.type()));
            }
            if (filter.minCapacity() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("capacity"), filter.minCapacity()));
            }
            if (filter.location() != null && !filter.location().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("location")), "%" + escapeLike(filter.location().trim()) + "%", '\\'));
            }
            if (filter.active() != null) {
                predicates.add(cb.equal(root.get("active"), filter.active()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // para que un % o _ escrito por el usuario se busque tal cual y no como comodin
    private static String escapeLike(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
