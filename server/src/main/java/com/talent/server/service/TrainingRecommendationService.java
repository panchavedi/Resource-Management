package com.talent.server.service;

import com.talent.server.dto.request.CreateTrainingRecommendationRequest;
import com.talent.server.dto.response.TrainingRecommendationResponse;
import com.talent.server.entity.Employee;
import com.talent.server.entity.LearningResource;
import com.talent.server.entity.ProjectDemand;
import com.talent.server.entity.Skill;
import com.talent.server.entity.TrainingRecommendation;
import com.talent.server.enums.RecommendationSource;
import com.talent.server.enums.TrainingStatus;
import com.talent.server.exception.ApiException;
import com.talent.server.exception.ErrorCode;
import com.talent.server.repository.DemandRepository;
import com.talent.server.repository.EmployeeRepository;
import com.talent.server.repository.LearningResourceRepository;
import com.talent.server.repository.SkillRepository;
import com.talent.server.repository.Specifications.Training;
import com.talent.server.repository.TrainingRecommendationRepository;
import com.talent.server.service.LearningPathService.LearningPath;
import org.springframework.data.domain.Sort;
import com.talent.server.repository.Specifications;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class TrainingRecommendationService {

        private final TrainingRecommendationRepository repository;
        private final EmployeeRepository employeeRepository;
        private final SkillRepository skillRepository;
        private final DemandRepository demandRepository;
        private final LearningResourceRepository learningResourceRepository;

        public TrainingRecommendationService(TrainingRecommendationRepository repository,
                        EmployeeRepository employeeRepository, SkillRepository skillRepository,
                        DemandRepository demandRepository,
                        LearningResourceRepository learningResourceRepository) {
                this.repository = repository;
                this.employeeRepository = employeeRepository;
                this.skillRepository = skillRepository;
                this.demandRepository = demandRepository;
                this.learningResourceRepository = learningResourceRepository;
        }

        public List<TrainingRecommendationResponse> search(Long employeeId, Collection<TrainingStatus> statuses,
                        String category) {
                Specification<TrainingRecommendation> spec = Specifications.allOf(Training.employee(employeeId),
                                Training.statusIn(statuses), Training.skillCategory(category));
                return repository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                                .map(TrainingRecommendationResponse::of).toList();
        }

        @Transactional
        public TrainingRecommendationResponse create(CreateTrainingRecommendationRequest request, String actor) {
                Employee employee = employeeRepository.findById(request.employeeId())
                                .orElseThrow(() -> ApiException.notFound("Employee", request.employeeId()));
                Skill skill = skillRepository.findById(request.skillId())
                                .orElseThrow(() -> ApiException.notFound("Skill", request.skillId()));
                ProjectDemand demand = request.demandId() == null ? null
                                : demandRepository.findById(request.demandId())
                                                .orElseThrow(() -> ApiException.notFound("Demand", request.demandId()));
                LearningResource resource = request.learningResourceId() == null ? null
                                : learningResourceRepository.findById(request.learningResourceId())
                                                .orElseThrow(() -> ApiException.notFound("Learning resource",
                                                                request.learningResourceId()));
                if (repository.findFirstByEmployeeIdAndSkillIdAndStatusIn(employee.getId(), skill.getId(),
                                TrainingStatus.OPEN).isPresent()) {
                        throw ApiException.duplicate("%s already has an open recommendation for %s"
                                        .formatted(employee.getName(), skill.getName()));
                }
                Integer current = employee.findSkill(skill.getId()).map(es -> es.getProficiencyLevel()).orElse(null);
                TrainingRecommendation saved = repository
                                .save(new TrainingRecommendation(employee, skill, demand, resource,
                                                current, request.targetLevel(), request.reason().trim(),
                                                RecommendationSource.MANUAL, actor));
                return TrainingRecommendationResponse.of(saved);
        }

        @Transactional
        public TrainingRecommendationResponse updateStatus(Long id, TrainingStatus status) {
                TrainingRecommendation recommendation = repository.findById(id)
                                .orElseThrow(() -> ApiException.notFound("Training recommendation", id));
                if (!TrainingStatus.OPEN.contains(recommendation.getStatus())) {
                        throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                                        "Recommendation is already %s".formatted(recommendation.getStatus()));
                }
                recommendation.changeStatus(status);
                return TrainingRecommendationResponse.of(recommendation);
        }

        /**
         * Persists agent-suggested learning paths. One open recommendation per employee
         * and skill: existing
         * open recommendations are kept as they are (reported back), never duplicated.
         */
        @Transactional
        public List<TrainingRecommendationResponse> recordFromAgent(List<LearningPath> paths, Long demandId,
                        String actor) {
                ProjectDemand demand = demandId == null ? null : demandRepository.findById(demandId).orElse(null);
                List<TrainingRecommendationResponse> result = new ArrayList<>();
                for (LearningPath path : paths) {
                        var existing = repository.findFirstByEmployeeIdAndSkillIdAndStatusIn(path.employeeId(),
                                        path.skillId(),
                                        TrainingStatus.OPEN);
                        if (existing.isPresent()) {
                                result.add(TrainingRecommendationResponse.of(existing.get()));
                                continue;
                        }
                        Employee employee = employeeRepository.getReferenceById(path.employeeId());
                        Skill skill = skillRepository.getReferenceById(path.skillId());
                        LearningResource first = path.steps().isEmpty() ? null
                                        : learningResourceRepository.getReferenceById(path.steps().getFirst().id());
                        String reason = "%s gap for %s: requires level %d, stored %s".formatted(
                                        path.mandatory() ? "Mandatory" : "Optional",
                                        demand == null ? "demand" : demand.getProjectName() + " / " + demand.getRole(),
                                        path.targetLevel(),
                                        path.currentLevel() == null ? "no record" : "level " + path.currentLevel());
                        TrainingRecommendation saved = repository
                                        .save(new TrainingRecommendation(employee, skill, demand, first,
                                                        path.currentLevel(), path.targetLevel(), reason,
                                                        RecommendationSource.AI_AGENT, actor));
                        result.add(TrainingRecommendationResponse.of(saved));
                }
                return result;
        }
}
