package com.mcpgateway.repository;

import com.mcpgateway.domain.entity.HostGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HostGroupRepository extends JpaRepository<HostGroup, Long> {

    Optional<HostGroup> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);
}
