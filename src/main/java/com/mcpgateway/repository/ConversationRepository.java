package com.mcpgateway.repository;

import com.mcpgateway.domain.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /**
     * One conversation and its turns, but only for the person who owns it.
     *
     * <p>Ownership is in the query rather than checked afterwards. A findByRef followed by
     * an if-statement is one forgotten branch away from serving someone else's history, and
     * a row that is not yours should not be loaded in the first place.
     */
    @EntityGraph(attributePaths = "turns")
    Optional<Conversation> findByConversationRefAndOwnerId(String conversationRef, Long ownerId);

    /** The owner's conversations, newest activity first. Turns are not needed for a list. */
    Page<Conversation> findByOwnerId(Long ownerId, Pageable pageable);

    /**
     * The owner's conversations that mention something, newest activity first.
     *
     * <p>Over the questions as well as the title. A title is the first thing that was
     * asked, and what somebody is looking for a week later is usually not that — it is the
     * table they queried or the host they touched, which is in a turn halfway down. Titles
     * alone would find almost nothing anybody actually searches for.
     *
     * <p>{@code distinct}, because a conversation with four matching turns is one
     * conversation.
     */
    @Query("""
            select distinct c from Conversation c
            left join c.turns t
            where c.owner.id = :ownerId
              and (lower(c.title) like :pattern
                   or lower(t.prompt) like :pattern
                   or lower(t.statement) like :pattern)
            """)
    Page<Conversation> search(Long ownerId, String pattern, Pageable pageable);
}
