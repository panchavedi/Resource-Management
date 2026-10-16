package com.talent.server.repository;

import com.talent.server.entity.TrainingRecommendation;
import com.talent.server.enums.TrainingStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TrainingRecommendationRepository
        extends JpaRepository<TrainingRecommendation, Long>, JpaSpecificationExecutor<TrainingRecommendation> {

    Optional<TrainingRecommendation> findFirstByEmployeeIdAndSkillIdAndStatusIn(Long employeeId, Long skillId,
                                                                                Collection<TrainingStatus> statuses);

    @Override
    @EntityGraph(attributePaths = {"employee", "skill", "demand", "learningResource"})
    List<TrainingRecommendation> findAll(Specification<TrainingRecommendation> spec, Sort sort);
}
