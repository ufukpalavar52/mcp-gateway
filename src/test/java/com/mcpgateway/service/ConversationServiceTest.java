package com.mcpgateway.service;

import com.mcpgateway.client.McpServerClient;
import com.mcpgateway.common.exception.ResourceNotFoundException;
import com.mcpgateway.domain.entity.Conversation;
import com.mcpgateway.domain.entity.ConversationTurn;
import com.mcpgateway.domain.entity.User;
import com.mcpgateway.domain.enums.UserRole;
import com.mcpgateway.repository.ConversationRepository;
import com.mcpgateway.repository.ConversationTurnRepository;
import com.mcpgateway.property.ConversationProperties;
import com.mcpgateway.repository.UserRepository;
import com.mcpgateway.security.AuthenticatedUser;
import com.mcpgateway.service.impl.ConversationServiceImpl;
import com.mcpgateway.service.intf.ConversationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The console history that closing a tab used to erase.
 *
 * <p>Two properties are the point of this class. A conversation belongs to one person and
 * nobody else can read it; and what it keeps is the conversation, not a second copy of the
 * data a run returned.
 */
class ConversationServiceTest {

    private static final Long ME = 7L;

    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final McpServerClient mcpServer = mock(McpServerClient.class);
    private final ConversationProperties properties = new ConversationProperties();
    private final ConversationTurnRepository turns = mock(ConversationTurnRepository.class);
    private final com.mcpgateway.service.GoalProgress goalProgress =
            new com.mcpgateway.service.GoalProgress();
    private final ConversationService service = new ConversationServiceImpl(
            conversations, turns, users, mcpServer, properties, goalProgress);

    @BeforeEach
    void signIn() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AuthenticatedUser(ME, "me@example.com", UserRole.ADMIN, "t"),
                        null, List.of()));

        User me = new User();
        me.setId(ME);
        when(users.findById(ME)).thenReturn(Optional.of(me));
        when(conversations.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aQuestionAndItsAnswerAreKept() {
        var recorded = record(null, "en cok hesabi olan 3 domaini ver", "", true,
                result("bireysel_local_yaanidb", "planned",
                        "SELECT domain, count(*) FROM tblAccounts GROUP BY domain", "run-42"),
                null);

        Conversation saved = savedConversation();

        assertThat(recorded.conversationRef()).startsWith("conv_");
        assertThat(saved.getTitle()).isEqualTo("en cok hesabi olan 3 domaini ver");
        assertThat(saved.getTurns()).singleElement().satisfies(turn -> {
            assertThat(turn.getPrompt()).isEqualTo("en cok hesabi olan 3 domaini ver");
            assertThat(turn.getToolName()).isEqualTo("bireysel_local_yaanidb");
            assertThat(turn.getStatement()).contains("tblAccounts");
            assertThat(turn.isExecuted()).isTrue();
        });
    }

    @Test
    void theOutputIsPointedAtRatherThanCopied() {
        /*
         * The turn keeps a reference to the run and nothing of what it returned. Two copies
         * of a result set would mean two places to look when personal data has to be found
         * or removed, and the second is the one nobody remembers.
         */
        record(null, "ilk 100 kullaniciyi getir", null, true,
                result("bireysel_local_yaanidb", "planned", "SELECT * FROM tblAccounts", "run-9"),
                null);

        assertThat(savedConversation().getTurns().getFirst().getRunRef()).isEqualTo("run-9");
    }

    @Test
    void aQuestionThatNeverReachedAnAnswerIsStillPartOfTheConversation() {
        // "The MCP server was down when I asked this" is what somebody coming back an hour
        // later needs to see. A history that dropped these looks like nothing was asked.
        record(null, "kac tane hesap var", null, false, null,
                "Could not route the prompt: 500 INTERNAL_SERVER_ERROR");

        assertThat(savedConversation().getTurns()).singleElement().satisfies(turn -> {
            assertThat(turn.getPrompt()).isEqualTo("kac tane hesap var");
            assertThat(turn.getFailure()).contains("500");
            assertThat(turn.getToolName()).isNull();
        });
    }

    @Test
    void aTurnPinnedToAToolRemembersThatAPersonChoseIt() {
        // Whether the operator picked the tool or the model did is the interesting part of
        // a routing decision, and one column could not say both.
        record(null, "hesaplari say", "bireysel_local_yaanidb", false,
                result("bireysel_local_yaanidb", "planned", "SELECT count(*)", null), null);

        var turn = savedConversation().getTurns().getFirst();

        assertThat(turn.getPinnedTool()).isEqualTo("bireysel_local_yaanidb");
        assertThat(turn.getToolName()).isEqualTo("bireysel_local_yaanidb");
    }

    @Test
    void aStepThatCouldNotBePlannedSaysWhy() {
        /*
         * Only the router's problem used to be kept. A goal-loop step whose plan came back
         * incomplete recorded a turn with an empty problem — and because an unplanned step
         * never runs, there is no result to wake the loop again. The goal stopped, the
         * conversation showed a blank card, and nothing anywhere said why.
         */
        record(null, "id si 59 olan kullaniciyi sil", null, true,
                planned("rock_linux", "incomplete", null,
                        List.of("Which of this tool's actions the request needs could not "
                                + "be decided; nothing was planned")),
                null);

        assertThat(savedConversation().getTurns().getFirst().getProblem())
                .contains("could not be decided");
    }

    @Test
    void theRoutersOwnProblemStillWins() {
        // "No published tool matches this request" is about the request, not about a plan
        // that does not exist. It is the more useful sentence and it comes first.
        record(null, "bugun hava nasil", null, false,
                new McpServerClient.PromptResult(
                        null, "", Map.of(), "", "No published tool matches this request",
                        null, Map.of("problems", List.of("something else")), null),
                null);

        assertThat(savedConversation().getTurns().getFirst().getProblem())
                .isEqualTo("No published tool matches this request");
    }

    /** A result carrying a plan, with the plan's own problems. */
    private McpServerClient.PromptResult planned(String tool, String status,
                                                 String resolved, List<String> problems) {
        return new McpServerClient.PromptResult(
                tool, "", Map.of(), "", null, status,
                Map.of("actions", resolved == null
                                ? List.of()
                                : List.of(Map.of("resolved", resolved, "kind", "rest")),
                        "problems", problems),
                null);
    }

    @Test
    void searchingLooksInsideTheConversationNotOnlyAtItsTitle() {
        /*
         * A title is the first thing that was asked. What somebody looks for a week later
         * is usually not that — it is the table they queried or the host they touched,
         * which is in a turn halfway down. Titles alone would find almost nothing anybody
         * actually searches for.
         */
        when(conversations.search(any(), any(), any())).thenReturn(Page.empty());

        service.findMine("tblAccounts", PageRequest.of(0, 20));

        ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
        Mockito.verify(conversations).search(Mockito.eq(ME), pattern.capture(), any());

        // Lower cased and wrapped, because the query compares lower(...) like :pattern.
        assertThat(pattern.getValue()).isEqualTo("%tblaccounts%");
        Mockito.verify(conversations, Mockito.never()).findByOwnerId(any(), any());
    }

    @Test
    void anEmptySearchIsEverythingRatherThanNothing() {
        // The sidebar asks with no words in the box, which is not a search for "".
        when(conversations.findByOwnerId(any(), any())).thenReturn(Page.empty());

        service.findMine("   ", PageRequest.of(0, 20));

        Mockito.verify(conversations).findByOwnerId(Mockito.eq(ME), any());
        Mockito.verify(conversations, Mockito.never()).search(any(), any(), any());
    }

    @Test
    void somebodyElsesConversationIsNotFound() {
        // Scoped in the query, not checked afterwards: a findByRef plus an if-statement is
        // one forgotten branch away from serving another person's questions.
        when(conversations.findByConversationRefAndOwnerId("conv_theirs", ME))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.open("conv_theirs"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void appendingToAConversationThatIsNotMineStartsANewOne() {
        /*
         * Rather than failing. A console should not throw away a question somebody just
         * typed because the session behind it was deleted in another tab — and it must not
         * append to a stranger's conversation either.
         */
        when(conversations.findByConversationRefAndOwnerId("conv_theirs", ME))
                .thenReturn(Optional.empty());

        var recorded = record("conv_theirs", "hesaplari say", null, false,
                result("t", "planned", "SELECT 1", null), null);

        assertThat(recorded.conversationRef()).isNotEqualTo("conv_theirs");
        assertThat(savedConversation().getOwner().getId()).isEqualTo(ME);
    }

    @Test
    void whatWasAskedBeforeTravelsWithTheNextQuestion() {
        /*
         * The gap that made the console a list of unrelated questions. It kept a thread and
         * looked like a chat, but every prompt reached the model alone: "peki ya
         * example.com icin?" arrived with nothing to continue.
         */
        Conversation conversation = mineWith(
                turn("kac tane hesap var", "select count(*) from tblAccounts", null),
                turn("peki ya example.com", "select count(*) where domain = 'x'", null));

        when(conversations.findByConversationRefAndOwnerId("conv_mine", ME))
                .thenReturn(Optional.of(conversation));

        var thread = service.thread("conv_mine");

        assertThat(thread.turns()).hasSize(2);
        assertThat(thread.turns().getFirst().prompt()).isEqualTo("kac tane hesap var");
        assertThat(thread.turns().getFirst().statement()).contains("count(*)");
    }

    @Test
    void aTurnThatFailedIsNotOfferedAsSomethingToContinue() {
        // It has no answer. Offering it as an antecedent invites the model to repeat the
        // attempt that already did not work.
        Conversation conversation = mineWith(
                turn("kac tane hesap var", "select count(*) from tblAccounts", null),
                turn("bir daha dene", null, "Could not route the prompt: 500"));

        when(conversations.findByConversationRefAndOwnerId("conv_mine", ME))
                .thenReturn(Optional.of(conversation));

        assertThat(service.thread("conv_mine").turns())
                .singleElement()
                .satisfies(prior -> assertThat(prior.prompt()).isEqualTo("kac tane hesap var"));
    }

    @Test
    void onlyTheLastFewTravel() {
        /*
         * Counted after the failures are dropped. Skipping by the unfiltered size would
         * throw away good turns to make room for ones that are never sent.
         */
        Conversation conversation = mineWith(
                turn("bir", "select 1", null),
                turn("iki", null, "boom"),
                turn("uc", "select 3", null),
                turn("dort", "select 4", null));

        when(conversations.findByConversationRefAndOwnerId("conv_mine", ME))
                .thenReturn(Optional.of(conversation));

        properties.setThreadLimit(2);
        properties.setSummarise(false);

        assertThat(service.thread("conv_mine").turns())
                .extracting(McpServerClient.PriorTurn::prompt)
                .containsExactly("uc", "dort");
    }

    @Test
    void aStrangersThreadIsNotHandedToTheModel() {
        // Empty rather than an error: a new conversation starts either way, and it should
        // start without somebody else's questions in it.
        when(conversations.findByConversationRefAndOwnerId("conv_theirs", ME))
                .thenReturn(Optional.empty());

        assertThat(service.thread("conv_theirs").turns()).isEmpty();
    }

    @Test
    void aFirstQuestionHasNothingBehindIt() {
        assertThat(service.thread(null).turns()).isEmpty();
    }

    @Test
    void whatSlidOutOfTheWindowIsFoldedIntoASummary() {
        /*
         * The window slides, and without this the beginning of a long conversation — the
         * question that established what everyone has been talking about — simply falls
         * off the end of it.
         */
        properties.setThreadLimit(1);
        properties.setSummaryBatch(2);

        Conversation conversation = mineWith(
                turn("bir", "select 1", null),
                turn("iki", "select 2", null),
                turn("uc", "select 3", null));
        stamp(conversation);

        when(conversations.findByConversationRefAndOwnerId("conv_mine", ME))
                .thenReturn(Optional.of(conversation));
        when(mcpServer.summarise(any(), any())).thenReturn("Hesaplar konusuldu.");

        var thread = service.thread("conv_mine");

        assertThat(thread.summary()).isEqualTo("Hesaplar konusuldu.");
        assertThat(thread.turns()).extracting(McpServerClient.PriorTurn::prompt)
                .containsExactly("uc");
        assertThat(conversation.getSummarisedThroughId()).isEqualTo(2L);
    }

    @Test
    void aTurnIsNeverFoldedInTwice() {
        // summarisedThroughId is what keeps the same question from being summarised again
        // on every subsequent turn, which would drift the summary a little each time.
        properties.setThreadLimit(1);
        properties.setSummaryBatch(1);

        Conversation conversation = mineWith(
                turn("bir", "select 1", null),
                turn("iki", "select 2", null));
        stamp(conversation);
        conversation.setSummary("Zaten ozetlendi.");
        conversation.setSummarisedThroughId(1L);

        when(conversations.findByConversationRefAndOwnerId("conv_mine", ME))
                .thenReturn(Optional.of(conversation));

        var thread = service.thread("conv_mine");

        assertThat(thread.summary()).isEqualTo("Zaten ozetlendi.");
        Mockito.verify(mcpServer, Mockito.never()).summarise(any(), any());
    }

    @Test
    void summarisingIsBatchedRatherThanDoneEveryTurn() {
        // A model call per question, for the oldest and least consulted part of the
        // conversation, is the thing the batch exists to avoid.
        properties.setThreadLimit(1);
        properties.setSummaryBatch(5);

        Conversation conversation = mineWith(
                turn("bir", "select 1", null),
                turn("iki", "select 2", null));
        stamp(conversation);

        when(conversations.findByConversationRefAndOwnerId("conv_mine", ME))
                .thenReturn(Optional.of(conversation));

        assertThat(service.thread("conv_mine").summary()).isEmpty();
        Mockito.verify(mcpServer, Mockito.never()).summarise(any(), any());
    }

    @Test
    void aSummaryThatCouldNotBeWrittenLeavesTheTurnsToBeOfferedAgain() {
        /*
         * The MCP server was down. Marking them folded would lose them for good; leaving
         * them unmarked costs one more attempt next time, which is the cheaper mistake.
         */
        properties.setThreadLimit(1);
        properties.setSummaryBatch(1);

        Conversation conversation = mineWith(
                turn("bir", "select 1", null),
                turn("iki", "select 2", null));
        stamp(conversation);

        when(conversations.findByConversationRefAndOwnerId("conv_mine", ME))
                .thenReturn(Optional.of(conversation));
        when(mcpServer.summarise(any(), any())).thenAnswer(call -> call.getArgument(0));

        assertThat(service.thread("conv_mine").summary()).isEmpty();
        assertThat(conversation.getSummarisedThroughId()).isNull();
    }

    /** Gives the turns the ids a database would have. */
    private void stamp(Conversation conversation) {
        long id = 1;
        for (ConversationTurn turn : conversation.getTurns()) {
            turn.setId(id++);
        }
    }

    @Test
    void whatTheQueryNarrowedOnIsKeptWithTheTurn() {
        /*
         * A warning is worth more later than at the time. Whether "493" counted everything
         * is not what the person watching it appear asks; it is what somebody asks a week
         * afterwards, by which point the warning had scrolled off the screen.
         */
        record(null, "kac tane hesap var", null, true,
                withWarnings(Map.of("code", "unrequested_filter",
                        "detail", "domain = 'bireysel'")), null);

        assertThat(savedConversation().getTurns().getFirst().getWarnings())
                .singleElement()
                .satisfies(warning -> {
                    assertThat(warning).containsEntry("code", "unrequested_filter");
                    assertThat(warning).containsEntry("detail", "domain = 'bireysel'");
                });
    }

    @Test
    void aTurnWithNothingToWarnAboutStoresNothing() {
        // Null rather than an empty list: nothing to say and no row to read are the same
        // thing, and the column stays empty for the turns that had none.
        record(null, "kac tane hesap var", null, true,
                result("t", "planned", "select count(*)", null), null);

        assertThat(savedConversation().getTurns().getFirst().getWarnings()).isNull();
    }

    @SafeVarargs
    private McpServerClient.PromptResult withWarnings(Map<String, Object>... warnings) {
        return new McpServerClient.PromptResult(
                "bireysel_local_yaanidb",
                "",
                Map.of(),
                "",
                null,
                "planned",
                Map.of("actions", List.of(Map.of("resolved", "select count(*)", "kind", "db")),
                        "warnings", List.of(warnings)),
                null);
    }

    private Conversation mineWith(ConversationTurn... turns) {
        Conversation conversation = Conversation.builder()
                .conversationRef("conv_mine")
                .turns(new java.util.ArrayList<>(List.of(turns)))
                .build();
        conversation.setId(1L);
        return conversation;
    }

    @Test
    void aProposedStepCanBeApprovedByThePersonWhoseGoalItIs() {
        proposedStep(88L, 80L);

        assertThat(service.proposalOf(88L)).satisfies(proposal -> {
            assertThat(proposal.goalTurnId()).isEqualTo(80L);
            assertThat(proposal.request()).isEqualTo("apache servisini baslat");
            assertThat(proposal.statement()).isEqualTo("systemctl enable --now httpd");
            assertThat(proposal.toolName()).isEqualTo("rock_linux");
        });
    }

    @Test
    void aConversationSaysWhenAStepIsBeingDecidedForIt() {
        /*
         * Deciding what comes after a finished run is two model calls, and the console had
         * nothing to show for either: a result, then a silence, then an approval card.
         *
         * The first attempt at this read `conversation_turns.deferred`, which turned out
         * never to be written once the selection began choosing one action at a time. The
         * indicator was inert — worse than absent, because it looked finished.
         */
        when(conversations.findByConversationRefAndOwnerId("conv_mine", ME))
                .thenReturn(Optional.of(mineWith()));

        goalProgress.deciding("conv_mine");

        assertThat(service.open("conv_mine").continuing()).isTrue();
    }

    @Test
    void aConversationNobodyIsDecidingForSaysSo() {
        when(conversations.findByConversationRefAndOwnerId("conv_mine", ME))
                .thenReturn(Optional.of(mineWith()));

        assertThat(service.open("conv_mine").continuing()).isFalse();
    }

    @Test
    void aDecisionThatWasSettledIsNoLongerClaimed() {
        // The loop clears it in a finally, so a step that failed to plan does not leave a
        // spinner nobody can dismiss.
        when(conversations.findByConversationRefAndOwnerId("conv_mine", ME))
                .thenReturn(Optional.of(mineWith()));

        goalProgress.deciding("conv_mine");
        goalProgress.settled("conv_mine");

        assertThat(service.open("conv_mine").continuing()).isFalse();
    }

    @Test
    void aProposalCarriesTheValuesItWasPlannedWith() {
        /*
         * Approving re-plans rather than replays, and the only thing going into that
         * re-plan was the turn's sentence. A step the loop wrote has no values in its
         * sentence: asked to act on "the first user found with first_name 'Yigit'", routing
         * supplied an id of its own, which did not match the command on screen, so `expect`
         * stopped the dispatch. The card fell back to waiting and nothing said why — which
         * read, to the operator, as approve doing nothing at all.
         */
        ConversationTurn step = proposedStep(88L, 80L);
        step.setArguments(new java.util.LinkedHashMap<>(Map.of("id", "59")));

        assertThat(service.proposalOf(88L).arguments()).containsEntry("id", "59");
    }

    @Test
    void aProposalCarriesTheActionItWasPlannedAs() {
        /*
         * A definition holds several actions and the choice is made afresh on every plan.
         * Re-planned from the sentence alone, an approved DELETE came back as the listing
         * action — the screen said one thing, the re-plan said another, and `expect` refused
         * the dispatch. What a person approves is a command, not a sentence.
         */
        ConversationTurn step = proposedStep(88L, 80L);
        step.setActionId(64L);

        assertThat(service.proposalOf(88L).actionId()).isEqualTo(64L);
    }

    @Test
    void theActionThePlanSettledOnIsKeptWithTheTurn() {
        // The one that resolved to something. An action the plan set aside carries no
        // command, and the turn is about the one that did.
        record(null, "59 numarali kullaniciyi sil", null, true,
                new McpServerClient.PromptResult(
                        "users", "", Map.of("id", 59), "", null, "planned",
                        Map.of("actions", List.of(
                                Map.of("action_id", 61, "resolved", "", "kind", "rest"),
                                Map.of("action_id", 64, "resolved", "DELETE /users/59",
                                        "kind", "rest"))),
                        null),
                null);

        assertThat(savedConversation().getTurns().getFirst().getActionId()).isEqualTo(64L);
    }

    @Test
    void aProposalWithNoValuesCarriesNoneRatherThanNull() {
        // Most proposals have none, and the caller passes this straight into a request.
        proposedStep(88L, 80L);

        assertThat(service.proposalOf(88L).arguments()).isEmpty();
    }

    @Test
    void whatThePlanRanOnIsKeptWithTheTurn() {
        // Read back off the result rather than off what the caller sent: a value the loop
        // read out of an answer and one routing found in a sentence are the same fact by
        // the time there is a plan, and the plan is the thing being approved.
        record(null, "59 numarali kullaniciyi sil", null, true,
                new McpServerClient.PromptResult(
                        "users", "", Map.of("id", 59), "", null, "planned",
                        Map.of("actions", List.of(Map.of("resolved", "DELETE /users/59",
                                "kind", "rest"))),
                        null),
                null);

        assertThat(savedConversation().getTurns().getFirst().getArguments())
                .containsEntry("id", "59");
    }

    @Test
    void aStepOfSomebodyElsesGoalIsNotApprovable() {
        /*
         * The turn id arrives from a browser and may name any number at all. Approving
         * without this check would run a step of somebody else's goal — and open the
         * earlier steps' sealed output to plan the one after it.
         */
        ConversationTurn theirs = proposedStep(88L, 80L);
        theirs.getConversation().getOwner().setId(ME + 1);

        assertThatThrownBy(() -> service.proposalOf(88L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aStepThatAlreadyRanIsNotApprovableTwice() {
        // Otherwise the button runs the command again every time it is pressed. The run
        // reference is the signal, not the flag: a turn that asked to execute and was held
        // for approval also has executed set, and that one is exactly what this approves.
        ConversationTurn ran = proposedStep(88L, 80L);
        ran.setExecuted(true);
        ran.setRunRef("run-1");

        assertThatThrownBy(() -> service.proposalOf(88L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aTypedQuestionThatWasOnlyPlannedIsNotAProposal() {
        /*
         * Nothing is waiting on it. The box was not ticked, so nobody asked for it to run
         * and there is nothing to say yes to — this is the commonest use of the console and
         * an approve button on every one of these would be noise.
         */
        ConversationTurn typed = proposedStep(88L, 80L);
        typed.setGoalTurn(null);
        typed.setExecuted(false);

        assertThatThrownBy(() -> service.proposalOf(88L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aTypedQuestionHeldForApprovalIsAProposal() {
        /*
         * They ticked the box and the action says a person sees the command first. What is
         * on screen is a plan nobody has agreed to yet, which is exactly the thing this
         * approves.
         */
        ConversationTurn held = proposedStep(88L, 80L);
        held.setGoalTurn(null);
        held.setExecuted(true);

        assertThat(service.proposalOf(88L)).satisfies(proposal -> {
            assertThat(proposal.request()).isEqualTo("apache servisini baslat");
            assertThat(proposal.goalTurnId()).isNull();
        });
    }

    @Test
    void aRejectedPlanIsNotSomethingToApprove() {
        // There is no command to say yes to: the guardrails refused the one there was.
        proposedStep(88L, 80L).setStatus("rejected");

        assertThatThrownBy(() -> service.proposalOf(88L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void approvingFillsInTheProposalRatherThanAnsweringBesideIt() {
        /*
         * Recording the approval as a second turn left the first exactly as it was: still
         * planned, still with no run behind it, so still a proposal. The button stayed on
         * screen and every press ran the command again — which the operator saw as the
         * same command asking to be approved over and over.
         */
        ConversationTurn proposal = proposedStep(88L, 80L);

        var recorded = service.complete(88L, result("rock_linux", "planned",
                "systemctl enable --now httpd", "run-7"), null);

        assertThat(recorded.turnId()).isEqualTo(88L);
        assertThat(proposal.getRunRef()).isEqualTo("run-7");
        assertThat(proposal.isExecuted()).isTrue();

        // No second turn was appended: the conversation has the one it started with.
        assertThat(proposal.getConversation().getTurns()).isEmpty();
    }

    @Test
    void anApprovedProposalStopsOfferingToRunAgain() {
        proposedStep(88L, 80L);
        service.complete(88L, result("rock_linux", "planned",
                "systemctl enable --now httpd", "run-7"), null);

        assertThatThrownBy(() -> service.proposalOf(88L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void anApprovalThatNeverReachedTheServerIsKeptOnTheTurnItBelongsTo() {
        // Not on a new turn: the proposal is where somebody will look for what happened.
        ConversationTurn proposal = proposedStep(88L, 80L);

        service.complete(88L, null, "The MCP server was unreachable");

        assertThat(proposal.getFailure()).isEqualTo("The MCP server was unreachable");
        assertThat(proposal.getRunRef()).isNull();
    }

    @Test
    void aProposalCanBeTurnedDown() {
        /*
         * The other half of being asked. A screen that can only say yes is not asking; it
         * is waiting for somebody to give in.
         */
        ConversationTurn proposal = proposedStep(88L, 80L);

        service.decline(88L);

        assertThat(proposal.getStatus()).isEqualTo("declined");
        assertThat(proposal.getStatement()).isEqualTo("systemctl enable --now httpd");
    }

    @Test
    void aDeclinedProposalStopsBeingOffered() {
        proposedStep(88L, 80L);
        service.decline(88L);

        assertThatThrownBy(() -> service.proposalOf(88L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void somebodyElsesProposalCannotBeTurnedDownEither() {
        // The same check approving goes through: the id arrives from a browser.
        ConversationTurn theirs = proposedStep(88L, 80L);
        theirs.getConversation().getOwner().setId(ME + 1);

        assertThatThrownBy(() -> service.decline(88L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /** A step the loop wrote and did not run, findable by its id. */
    private ConversationTurn proposedStep(Long turnId, Long goalTurnId) {
        User owner = new User();
        owner.setId(ME);

        Conversation conversation = Conversation.builder()
                .conversationRef("conv_goal")
                .owner(owner)
                .turns(new java.util.ArrayList<>())
                .build();
        conversation.setId(2L);

        ConversationTurn goal = ConversationTurn.builder().prompt("apache kur ve calistir").build();
        goal.setId(goalTurnId);

        ConversationTurn step = ConversationTurn.builder()
                .conversation(conversation)
                .goalTurn(goal)
                .prompt("apache servisini baslat")
                .toolName("rock_linux")
                .statement("systemctl enable --now httpd")
                .status("planned")
                .executed(false)
                .build();
        step.setId(turnId);

        when(turns.findById(turnId)).thenReturn(Optional.of(step));
        return step;
    }

    private ConversationTurn turn(String prompt, String statement, String failure) {
        return ConversationTurn.builder()
                .prompt(prompt)
                .toolName("bireysel_local_yaanidb")
                .statement(statement)
                .failure(failure)
                .build();
    }

    /** record(), without the goal id every one of these tests passes as null. */
    private ConversationService.Recorded record(String ref, String prompt, String pinned,
                                                boolean executed,
                                                McpServerClient.PromptResult result,
                                                String failure) {
        return service.record(ref, prompt, pinned, executed, result, failure, null);
    }

    private Conversation savedConversation() {
        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        Mockito.verify(conversations).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private McpServerClient.PromptResult result(String tool, String status, String resolved,
                                                String runRef) {
        return new McpServerClient.PromptResult(
                tool,
                "",
                Map.of(),
                "matched on the table name",
                null,
                status,
                Map.of("actions", List.of(Map.of("resolved", resolved, "kind", "db"))),
                runRef == null ? null : Map.of("status", "queued", "run_id", runRef));
    }
}
