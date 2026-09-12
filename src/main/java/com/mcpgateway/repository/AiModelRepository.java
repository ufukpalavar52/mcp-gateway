package com.mcpgateway.repository;

import com.mcpgateway.domain.entity.AiModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiModelRepository extends JpaRepository<AiModel, Long> {

    Optional<AiModel> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    List<AiModel> findByEnabledTrue();
}
