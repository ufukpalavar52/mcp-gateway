package com.mcpgateway.repository;

import com.mcpgateway.domain.entity.Secret;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SecretRepository extends JpaRepository<Secret, Long> {

    Optional<Secret> findByName(String name);

    /** The name column is unique, so a new secret has to pick a free one. */
    boolean existsByName(String name);
}
