package com.mcpgateway.repository;

import com.mcpgateway.domain.entity.Action;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActionRepository extends JpaRepository<Action, Long> {

    List<Action> findByDefinitionIdOrderByPositionAsc(Long definitionId);

    long countByHostGroupId(Long hostGroupId);
}
