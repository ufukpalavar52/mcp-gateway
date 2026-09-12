package com.mcpgateway.repository;

import com.mcpgateway.domain.entity.RunTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RunTargetRepository extends JpaRepository<RunTarget, Long> {

    List<RunTarget> findByRunIdOrderByBatchIndexAscIdAsc(Long runId);

    Optional<RunTarget> findByRunIdAndAddress(Long runId, String address);
}
