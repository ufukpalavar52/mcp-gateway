package com.mcpgateway.repository;

import com.mcpgateway.domain.entity.DefinitionPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DefinitionPermissionRepository extends JpaRepository<DefinitionPermission, Long> {

    Optional<DefinitionPermission> findByDefinitionIdAndUserId(Long definitionId, Long userId);

    List<DefinitionPermission> findByDefinitionId(Long definitionId);

    /** Every definition this person was named on, for filtering a listing in one query. */
    List<DefinitionPermission> findByUserId(Long userId);

    void deleteByDefinitionIdAndUserId(Long definitionId, Long userId);
}
