package com.talent.server.repository;

import com.talent.server.entity.Allocation;
import com.talent.server.entity.DemandSkill;
import com.talent.server.entity.Employee;
import com.talent.server.entity.EmployeeSkill;
import com.talent.server.entity.ProjectDemand;
import com.talent.server.entity.TrainingRecommendation;
import com.talent.server.enums.AllocationStatus;
import com.talent.server.enums.DemandPriority;
import com.talent.server.enums.DemandStatus;
import com.talent.server.enums.EmploymentStatus;
import com.talent.server.enums.TrainingStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Composable query filters. Each factory returns {@code null} for an absent filter and
 * {@link #allOf} skips nulls, so optional query parameters compose without branching.
 */
public final class Specifications {

    private Specifications() {
    }

    /** AND of the non-null specifications; matches everything when none is given. */
    @SafeVarargs
    public static <T> Specification<T> allOf(Specification<T>... specs) {
        return allOf(Arrays.asList(specs));
    }

    public static <T> Specification<T> allOf(List<Specification<T>> specs) {
        return specs.stream().filter(Objects::nonNull).reduce(Specification::and)
                .orElse((root, query, cb) -> cb.conjunction());
    }

    private static String like(String text) {
        return "%" + text.trim().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    // ------------------------------------------------------------------ employees
    public static final class Employees {

        private Employees() {
        }

        public static Specification<Employee> text(String query) {
            if (blank(query)) {
                return null;
            }
            String pattern = like(query);
            return (root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), pattern, '\\'),
                    cb.like(cb.lower(root.get("employeeCode")), pattern, '\\'),
                    cb.like(cb.lower(root.get("jobTitle")), pattern, '\\'));
        }

        public static Specification<Employee> department(String department) {
            return blank(department) ? null
                    : (root, q, cb) -> cb.equal(cb.lower(root.get("department")), department.trim().toLowerCase(Locale.ROOT));
        }

        public static Specification<Employee> location(String location) {
            return blank(location) ? null
                    : (root, q, cb) -> cb.equal(cb.lower(root.get("location")), location.trim().toLowerCase(Locale.ROOT));
        }

        public static Specification<Employee> statusIn(Collection<EmploymentStatus> statuses) {
            return statuses == null || statuses.isEmpty() ? null : (root, q, cb) -> root.get("employmentStatus").in(statuses);
        }

        /** Employee holds the skill at {@code minLevel} or above (minLevel defaults to 1). */
        public static Specification<Employee> hasSkill(Long skillId, Integer minLevel) {
            if (skillId == null) {
                return null;
            }
            int level = minLevel == null ? 1 : minLevel;
            return (root, q, cb) -> {
                Subquery<Long> sub = q.subquery(Long.class);
                var es = sub.from(EmployeeSkill.class);
                sub.select(es.get("employee").get("id")).where(
                        cb.equal(es.get("skill").get("id"), skillId),
                        cb.greaterThanOrEqualTo(es.get("proficiencyLevel"), level));
                return root.get("id").in(sub);
            };
        }
    }

    // ------------------------------------------------------------------ demands
    public static final class Demands {

        private Demands() {
        }

        public static Specification<ProjectDemand> statusIn(Collection<DemandStatus> statuses) {
            return statuses == null || statuses.isEmpty() ? null : (root, q, cb) -> root.get("status").in(statuses);
        }

        public static Specification<ProjectDemand> priority(DemandPriority priority) {
            return priority == null ? null : (root, q, cb) -> cb.equal(root.get("priority"), priority);
        }

        public static Specification<ProjectDemand> text(String query) {
            if (blank(query)) {
                return null;
            }
            String pattern = like(query);
            return (root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("role")), pattern, '\\'),
                    cb.like(cb.lower(root.get("projectName")), pattern, '\\'));
        }

        public static Specification<ProjectDemand> requiresSkill(Long skillId) {
            if (skillId == null) {
                return null;
            }
            return (root, q, cb) -> {
                Subquery<Long> sub = q.subquery(Long.class);
                var ds = sub.from(DemandSkill.class);
                sub.select(ds.get("demand").get("id")).where(cb.equal(ds.get("skill").get("id"), skillId));
                return root.get("id").in(sub);
            };
        }

        /** Demand period intersects [from, to]; either bound may be open. */
        public static Specification<ProjectDemand> overlaps(LocalDate from, LocalDate to) {
            if (from == null && to == null) {
                return null;
            }
            return (root, q, cb) -> cb.and(
                    to == null ? cb.conjunction() : cb.lessThanOrEqualTo(root.get("startDate"), to),
                    from == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get("endDate"), from));
        }
    }

    // ------------------------------------------------------------------ allocations
    public static final class Allocations {

        private Allocations() {
        }

        public static Specification<Allocation> statusIn(Collection<AllocationStatus> statuses) {
            return statuses == null || statuses.isEmpty() ? null : (root, q, cb) -> root.get("status").in(statuses);
        }

        public static Specification<Allocation> employee(Long employeeId) {
            return employeeId == null ? null : (root, q, cb) -> cb.equal(root.get("employee").get("id"), employeeId);
        }

        public static Specification<Allocation> demand(Long demandId) {
            return demandId == null ? null : (root, q, cb) -> cb.equal(root.get("demand").get("id"), demandId);
        }
    }

    // ------------------------------------------------------------------ training
    public static final class Training {

        private Training() {
        }

        public static Specification<TrainingRecommendation> employee(Long employeeId) {
            return employeeId == null ? null : (root, q, cb) -> cb.equal(root.get("employee").get("id"), employeeId);
        }

        public static Specification<TrainingRecommendation> statusIn(Collection<TrainingStatus> statuses) {
            return statuses == null || statuses.isEmpty() ? null : (root, q, cb) -> root.get("status").in(statuses);
        }

        public static Specification<TrainingRecommendation> skillCategory(String category) {
            if (blank(category)) {
                return null;
            }
            return (root, q, cb) -> {
                Join<Object, Object> skill = root.join("skill");
                return cb.equal(skill.get("category"), category);
            };
        }
    }
}
