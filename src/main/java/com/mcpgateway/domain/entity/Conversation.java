package com.mcpgateway.domain.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * One console session, kept so leaving the page does not end it.
 *
 * <p>The turns used to live in React state and nowhere else. Closing the tab lost the
 * question, the model's reasoning and the plan together — and a turn that was not executed
 * left no {@link Run} either, so for the commonest use of the console there was nothing
 * anywhere afterwards.
 *
 * <p>A conversation belongs to one person and is read by no one else. The sentences people
 * type here are questions about their own systems; sharing them by default would be a
 * decision nobody made.
 */
@Entity
@Table(name = "conversations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation extends BaseEntity {

    /**
     * The identifier the panel uses.
     *
     * <p>Opaque on purpose: the numeric id counts rows, and a URL carrying it would say how
     * many conversations everyone else has had.
     */
    @Column(name = "conversation_ref", nullable = false, unique = true)
    private String conversationRef;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    /** Taken from the first question asked, so a list of these can be scanned. */
    @Column(nullable = false)
    @Builder.Default
    private String title = "";

    /**
     * What everything older than the window came to.
     *
     * <p>The window slides, and without this the beginning of a long conversation — the
     * question that established what everyone has been talking about — simply falls off
     * the end of it.
     */
    @Column(columnDefinition = "text")
    private String summary;

    /**
     * The last turn folded into {@link #summary}.
     *
     * <p>Folding rather than re-reading: the turns that just dropped out go in with the
     * summary so far, so the thousandth question costs what the fifty-first did. This is
     * what keeps a turn from being folded in twice.
     */
    @Column(name = "summarised_through_id")
    private Long summarisedThroughId;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    @Builder.Default
    private List<ConversationTurn> turns = new ArrayList<>();
}
