package com.mcpgateway.repository;

import com.mcpgateway.domain.entity.Definition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DefinitionRepository extends JpaRepository<Definition, Long> {

    Optional<Definition> findByToolName(String toolName);

    boolean existsByToolName(String toolName);

    boolean existsByToolNameAndIdNot(String toolName, Long id);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    /** Loads the actions in the same round trip; used when serving a full definition. */
    @EntityGraph(attributePaths = {"actions", "model"})
    Optional<Definition> findWithActionsById(Long id);

    /**
     * The catalogue pushed to the MCP server: only definitions whose model is enabled too.
     *
     * <p>Actions and their host groups come along in the same round trip because every
     * one of them is mapped into the published payload; leaving them lazy would turn one
     * publish into a query per action.
     */
    @EntityGraph(attributePaths = {"model", "actions", "actions.hostGroup"})
    @Query("""
            select d from Definition d
            where d.enabled = true and d.model is not null and d.model.enabled = true
            order by d.toolName asc
            """)
    List<Definition> findPublishedTools();

    @EntityGraph(attributePaths = {"model"})
    Page<Definition> findAllBy(Pageable pageable);

    long countByModelId(Long modelId);

    List<Definition> findByModelId(Long modelId);

    /** Definitions whose SSH actions target the given host group. */
    @Query("select distinct a.definition from Action a where a.hostGroup.id = :groupId")
    List<Definition> findByHostGroupId(@Param("groupId") Long groupId);
}
