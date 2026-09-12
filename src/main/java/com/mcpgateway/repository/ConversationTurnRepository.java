package com.mcpgateway.repository;

import com.mcpgateway.domain.entity.ConversationTurn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationTurnRepository extends JpaRepository<ConversationTurn, Long> {

    /** The turn a run belongs to. One run per turn, so at most one row. */
    Optional<ConversationTurn> findByRunRef(String runRef);

    /** The steps taken for a goal, in the order they were taken. */
    List<ConversationTurn> findByGoalTurnIdOrderByIdAsc(Long goalTurnId);
}
