package com.talent.server.repository;

import com.talent.server.entity.AgentRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AgentRunRepository extends JpaRepository<AgentRun, Long> {

    @Query("select r from AgentRun r where (:demandId is null or r.demandId = :demandId) order by r.createdAt desc")
    List<AgentRun> findRecent(@Param("demandId") Long demandId, Pageable pageable);

    @Query("select r from AgentRun r left join fetch r.toolCalls where r.id = :id")
    Optional<AgentRun> findWithToolCallsById(@Param("id") Long id);
}
