package com.sujith.scheduler.repository;

import com.sujith.scheduler.model.Job;
import com.sujith.scheduler.model.JobStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a JPA {@link Specification} for filtering jobs by status, priority range, and
 * creation time range. All filters are optional and combined with AND semantics.
 */
public final class JobSpecification {

    private JobSpecification() {
    }

    public static Specification<Job> withFilters(JobStatus status,
                                                   Integer minPriority,
                                                   Integer maxPriority,
                                                   Instant from,
                                                   Instant to) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (minPriority != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("priority"), minPriority));
            }
            if (maxPriority != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("priority"), maxPriority));
            }
            if (from != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), to));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
