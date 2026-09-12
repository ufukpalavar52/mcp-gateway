package com.mcpgateway.repository;

import com.mcpgateway.domain.entity.Run;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RunRepository extends JpaRepository<Run, Long> {

    /**
     * The row a result belongs to.
     *
     * <p>By the executor's reference, not by id: the executor never saw the id. The column
     * is uniquely indexed, so this is a single-row lookup rather than a scan.
     */
    @EntityGraph(attributePaths = "targets")
    Optional<Run> findByActionRef(String actionRef);

    /** Every action of one dispatched job, in the order they were created. */
    @EntityGraph(attributePaths = {"targets", "definition", "action"})
    List<Run> findByRunRefOrderByIdAsc(String runRef);

    @EntityGraph(attributePaths = {"targets", "definition", "action"})
    Page<Run> findAllBy(Pageable pageable);

    /**
     * One run with its action already loaded.
     *
     * <p>For a caller outside a transaction. The plain {@code findById} hands back a lazy
     * proxy for the action, and reading its kind afterwards throws — which is how a failure
     * in the goal loop first arrived, as a message the broker then redelivered forever.
     */
    @EntityGraph(attributePaths = "action")
    Optional<Run> findWithActionById(Long id);
}
