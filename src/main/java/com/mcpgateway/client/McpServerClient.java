package com.mcpgateway.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mcpgateway.dto.response.DefinitionResponse;
import com.mcpgateway.property.McpServerProperties;
import com.mcpgateway.security.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;

/**
 * Calls the MCP server.
 *
 * <p>Two directions of one relationship: this service publishes the catalogue it owns,
 * and asks for a decision when a tool is invoked. The MCP server never calls back — it
 * has no credentials for this service and no reason to.
 *
 * <p>Failures are reported, never swallowed into a fake success: a caller has to be able
 * to tell "the MCP server is down" from "the plan was rejected", because only one of
 * those is worth retrying.
 */
@Slf4j
@Component
public class McpServerClient {

    private static final String TOKEN_HEADER = "X-MCP-Token";

    private final RestClient restClient;
    private final McpServerProperties properties;

    public McpServerClient(RestClient.Builder builder, McpServerProperties properties) {
        this.properties = properties;
        this.restClient = builder
                .baseUrl(properties.getUrl())
                .requestFactory(requestFactory(properties))
                .build();
    }

    /**
     * A request factory pinned to HTTP/1.1.
     *
     * <p>The JDK client defaults to HTTP/2 and opens with an {@code Upgrade: h2c}
     * handshake, withholding the request body until the server agrees. Uvicorn, which
     * serves the MCP server, does not implement that upgrade: it answers as HTTP/1.1 and
     * the body is never sent, so every request arrives empty and comes back a 422 that
     * blames the payload. Asking for 1.1 up front removes the negotiation entirely.
     *
     * <p>Scoped to this client rather than set globally, because it is a fact about the
     * MCP server rather than about how this service should talk to everything.
     */
    private static JdkClientHttpRequestFactory requestFactory(McpServerProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.getTimeout())
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.getTimeout());
        return factory;
    }

    /**
     * Replaces the MCP server's catalogue with the given definitions.
     *
     * <p>A full replacement: a definition left out of the list stops being callable.
     * That is what makes a deletion here take effect there, without a second protocol
     * for removals that a missed message could silently skip.
     *
     * @return how many tools the MCP server now publishes
     */
    public int publishCatalogue(List<DefinitionResponse> definitions) {
        try {
            PublishResponse response = restClient.put()
                    .uri("/api/v1/catalogue")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (properties.hasToken()) {
                            headers.set(TOKEN_HEADER, properties.getToken());
                        }
                    })
                    .body(Map.of("definitions", definitions))
                    .retrieve()
                    .body(PublishResponse.class);

            int published = response == null ? 0 : response.published();
            log.info("Published {} definition(s); MCP server now exposes {} tool(s)",
                    definitions.size(), published);
            return published;
        } catch (RestClientException ex) {
            throw new McpServerUnavailableException("Could not publish the catalogue", ex);
        }
    }

    /**
     * Asks the MCP server what a tool call resolves to.
     *
     * <p>The MCP server decides; it does not execute. Whether the plan is then carried
     * out is the executor's business, and today nothing does.
     */
    public ExecutionResult requestExecution(String toolName, Map<String, Object> arguments,
                                            String actor, Long actionId,
                                            String expect, List<String> expectAll) {
        try {
            ExecutionResult result = restClient.post()
                    .uri("/api/v1/executions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (properties.hasToken()) {
                            headers.set(TOKEN_HEADER, properties.getToken());
                        }
                    })
                    .body(executionBody(toolName, arguments, actor, actionId, expect, expectAll))
                    .retrieve()
                    .body(ExecutionResult.class);

            if (result == null) {
                throw new McpServerUnavailableException("The MCP server returned an empty body");
            }
            log.info("Tool {} resolved to status {}", toolName, result.status());
            return result;
        } catch (RestClientResponseException ex) {
            throw refusalOrOutage(ex, "Could not reach the MCP server");
        } catch (RestClientException ex) {
            throw new McpServerUnavailableException("Could not reach the MCP server", ex);
        }
    }

    /**
     * The execution payload.
     *
     * <p>A mutable map rather than {@code Map.of}, which rejects a null value: an
     * unnamed action is absent rather than null, so a definition with one action sends
     * nothing about a choice it does not have to make.
     */
    private static Map<String, Object> executionBody(String toolName,
                                                     Map<String, Object> arguments,
                                                     String actor, Long actionId,
                                                     String expect, List<String> expectAll) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("toolName", toolName);
        body.put("arguments", arguments == null ? Map.of() : arguments);
        body.put("actor", actor == null ? "" : actor);
        // The approval, when this call is one. Empty means the caller has not been shown
        // a command yet and is asking to see it.
        body.put("expect", expect == null ? "" : expect);
        body.put("expectAll", expectAll == null ? List.of() : expectAll);

        if (actionId != null) {
            body.put("actionId", actionId);
        }
        return body;
    }

    /**
     * Asks the MCP server to route a prompt to a tool.
     *
     * <p>The choice is a model's and the plan is the ordinary planning path's; this service
     * only carries the request and records what came back. Whether anything runs is the
     * caller's decision, sent along as {@code execute}.
     */
    public PromptResult routePrompt(String prompt, String actor, boolean execute,
                                    String toolName, List<PriorTurn> history,
                                    String summary, Object expect, boolean unattended,
                                    Object actionId, Map<String, String> arguments,
                                    List<String> allowedTools) {
        try {
            PromptResult result = restClient.post()
                    .uri("/api/v1/prompts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (properties.hasToken()) {
                            headers.set(TOKEN_HEADER, properties.getToken());
                        }
                    })
                    .body(request(prompt, actor, execute, toolName, history, summary, expect,
                            unattended, actionId, arguments, allowedTools))
                    .retrieve()
                    .body(PromptResult.class);

            if (result == null) {
                throw new McpServerUnavailableException("The MCP server returned an empty body");
            }

            log.info("Prompt routed to {} ({})",
                    result.toolName() == null ? "no tool" : result.toolName(), result.status());
            return result;
        } catch (RestClientResponseException ex) {
            throw refusalOrOutage(ex, "Could not route the prompt");
        } catch (RestClientException ex) {
            throw new McpServerUnavailableException("Could not route the prompt", ex);
        }
    }

    /**
     * The prompt payload.
     *
     * <p>A mutable map rather than {@code Map.of}: an unchosen tool is absent, and
     * {@code Map.of} refuses a null value rather than omitting the key.
     */
    private static Map<String, Object> request(
            String prompt, String actor, boolean execute, String toolName,
            List<PriorTurn> history, String summary, Object expect, boolean unattended,
            Object actionId, Map<String, String> arguments, List<String> allowedTools) {

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("prompt", prompt);
        body.put("actor", actor == null ? "" : actor);

        // For the MCP server's own log lines. The id, not the address: it answers "what did
        // this person do" the same way, and it keeps personal data out of a log store that
        // has no encryption and thirty days of retention.
        SecurityUtils.currentUserId().ifPresent(id -> body.put("actorId", String.valueOf(id)));
        body.put("execute", execute);

        // Sent only when somebody approved a particular command. Planning is not
        // deterministic: a step proposed as `systemctl start httpd` and approved on the
        // strength of it came back from the re-plan as the install command, and what ran
        // was not what anybody had agreed to. With this set, a plan that no longer reads
        // the same is not dispatched.
        // One command or several: a plan whose commands all resolve from the one sentence
        // is put in front of somebody whole, and then every one of them is what was agreed
        // to. The MCP server compares the whole set, so a re-plan that comes back holding
        // an action nobody was shown is refused rather than quietly run.
        if (expect instanceof java.util.Collection<?> many) {
            if (!many.isEmpty()) {
                body.put("expectAll", many);
            }
        } else if (expect != null && !String.valueOf(expect).isBlank()) {
            body.put("expect", String.valueOf(expect));
        }

        // Nobody typed this one. Which action a step lands on is only known once it has
        // been planned, so whether it needs approving cannot be decided here — the MCP
        // server holds anything unattended that changes something.
        if (unattended) {
            body.put("unattended", true);
        }

        // The one action this step is for, when it is already known. A loop taking up an
        // action the plan set aside does not need a model to choose it again — and every
        // call not made is one the rate limit does not count.
        // The tools this caller may run. Absent means no restriction — an empty list would
        // be indistinguishable from "this person may run nothing", and an administrator
        // would be offered an empty catalogue.
        if (allowedTools != null && !allowedTools.isEmpty()) {
            body.put("allowedTools", allowedTools);
        }

        if (actionId instanceof java.util.Collection<?> ids) {
            if (!ids.isEmpty()) {
                body.put("actionIds", ids);
            }
        } else if (actionId != null) {
            body.put("actionId", actionId);
        }

        // What the step planner read out of the previous answer. This was accepted as a
        // parameter and then never sent, so every value the loop had already found was
        // dropped here and the MCP server was left to recover it from a sentence that does
        // not contain it — which is where `DELETE /api/users/1` came from, for a goal whose
        // search had returned 59, 69, 86 and 97.
        if (arguments != null && !arguments.isEmpty()) {
            body.put("arguments", arguments);
        }

        if (toolName != null && !toolName.isBlank()) {
            body.put("toolName", toolName);
        }
        if (history != null && !history.isEmpty()) {
            body.put("history", history);
        }
        if (summary != null && !summary.isBlank()) {
            body.put("summary", summary);
        }
        return body;
    }

    /**
     * The next question a goal needs, or the news that it needs none.
     *
     * <p>What comes back is a sentence, not a plan. It goes back through the ordinary
     * prompt path, so every step is routed, planned, checked and masked exactly as a typed
     * question is — the loop adds a decision and no new way to reach a database.
     *
     * <p>An unreachable MCP server ends the loop rather than failing it. The steps already
     * taken stand and are in the conversation; what is lost is the next one, which nobody
     * has seen yet.
     */
    public Step nextStep(String goal, String toolName, List<TakenStep> steps,
                         List<PriorTurn> history, String summary, int limit, String kind,
                         String waitingFor) {
        try {
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("goal", goal);
            body.put("steps", steps);
            body.put("history", history);
            body.put("summary", summary == null ? "" : summary);
            body.put("limit", limit);
            body.put("kind", kind == null || kind.isBlank() ? "db" : kind);

            // An action the plan set aside and what it is waiting for. Sent when there is
            // one, and it changes the question being asked: not "is there more to do",
            // which the plan already answered, but "write the request for the thing that
            // was waiting, using what came back".
            if (waitingFor != null && !waitingFor.isBlank()) {
                body.put("waitingFor", waitingFor);
            }

            if (toolName != null && !toolName.isBlank()) {
                body.put("toolName", toolName);
            }

            Step step = restClient.post()
                    .uri("/api/v1/steps")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (properties.hasToken()) {
                            headers.set(TOKEN_HEADER, properties.getToken());
                        }
                    })
                    .body(body)
                    .retrieve()
                    .body(Step.class);

            return step == null
                    ? new Step(true, "", "The MCP server returned nothing", Map.of())
                    : step;
        } catch (RestClientException ex) {
            log.warn("A next step could not be decided: {}", ex.getMessage());
            return new Step(true, "", ex.getMessage(), Map.of());
        }
    }

    /** One more question, or the news that the goal is met. */
    public record Step(boolean done, String request, String reason,
                       Map<String, String> values) {

        /**
         * The inputs a waiting action needed, read out of the answers so far.
         *
         * <p>Empty for an ordinary step, where the request is the whole answer — and the
         * difference matters: asked for a sentence, the model described the situation and
         * left the value inside it, where routing could not reach it.
         *
         * <p>Absent is empty rather than null, because most steps have none.
         */
        public Map<String, String> values() {
            return values == null ? Map.of() : values;
        }
    }

    /**
     * A step already taken, and the shape of what it returned.
     *
     * <p>The shape, not the rows. "Total those" is answered by writing a SUM over the same
     * table, which needs the column names and not the values; sending the whole result
     * would put production data into every subsequent prompt to save an arithmetic the
     * database does better anyway.
     */
    public record TakenStep(String request,
                            String statement,
                            @JsonProperty("rowCount") Integer rowCount,
                            List<String> columns,
                            String sample,

                            /**
                             * What a shell step printed, trimmed.
                             *
                             * <p>A command's result has no columns and no rows. "Is httpd
                             * running" is answered by the words systemctl wrote, and a
                             * step planner shown only a row count would be choosing the
                             * next command with nothing to go on.
                             */
                            String output,

                            String problem) {
    }

    /**
     * Folds turns that have dropped out of the window into a running summary.
     *
     * <p>Never fatal. A conversation that could not update its summary still answers the
     * next question — it has its window — and the turns that failed to fold are offered
     * again next time. Refusing to answer because a summary is stale would be the wrong
     * trade in every direction.
     */
    public String summarise(String summary, List<PriorTurn> turns) {
        try {
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("summary", summary == null ? "" : summary);
            body.put("turns", turns);

            Map<?, ?> response = restClient.post()
                    .uri("/api/v1/summaries")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (properties.hasToken()) {
                            headers.set(TOKEN_HEADER, properties.getToken());
                        }
                    })
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            Object folded = response == null ? null : response.get("summary");
            return folded == null ? summary : String.valueOf(folded);
        } catch (RestClientException ex) {
            log.warn("A conversation could not be summarised: {}", ex.getMessage());
            return summary;
        }
    }

    /**
     * A question asked earlier in the same conversation, and what it became.
     *
     * <p>Sent so a follow-up has an antecedent. The console kept a thread and looked like a
     * chat, but every prompt arrived alone: "peki ya example.com icin?" reached the
     * router with nothing to continue, so it was routed — or refused — as though it were
     * the first thing anybody had said.
     *
     * <p>The statement travels and the result does not. A follow-up refers to the question
     * and to what ran; it does not refer to the rows, and putting a result set into every
     * subsequent prompt would copy production data somewhere new to answer nothing.
     */
    public record PriorTurn(String prompt,
                            @JsonProperty("toolName") String toolName,
                            String statement) {
    }

    /**
     * What a prompt turned into.
     *
     * <p>{@code toolName} is null when nothing matched, and {@code problem} says why. That
     * is a real answer rather than an error: a request that no tool serves is worth
     * reporting plainly, and forcing it into the nearest match would be worse.
     */
    public record PromptResult(@JsonProperty("tool_name") String toolName,
                               /**
                                * What the model said when no tool was the right thing to
                                * call. Separate from a plan on purpose: this is a model
                                * talking, not a system reporting.
                                */
                               String answer,
                               Map<String, Object> arguments,
                               String reasoning,
                               String problem,
                               String status,
                               Map<String, Object> plan,
                               Map<String, Object> dispatch) {
    }

    /**
     * Decides whether a response was a refusal or an outage.
     *
     * <p>A 4xx means the MCP server understood and said no — an unknown tool, a malformed
     * body — and passing it through unchanged keeps the answer meaning the same thing at
     * both ends. Anything else is this service's problem to report as its own.
     *
     * <p>The MCP server's own {@code detail} is preferred: it names the tool, and replacing
     * it with a generic phrase would throw away the only part the caller can act on.
     */
    private RuntimeException refusalOrOutage(RestClientResponseException ex, String context) {
        if (!ex.getStatusCode().is4xxClientError()) {
            return new McpServerUnavailableException(context, ex);
        }

        String detail = ex.getResponseBodyAsString();
        return new McpServerRefusedException(ex.getStatusCode(),
                detail == null || detail.isBlank() ? context : readDetail(detail));
    }

    /** Pulls FastAPI's {@code detail} out of an error body, falling back to the body. */
    private String readDetail(String body) {
        int marker = body.indexOf("\"detail\":");
        if (marker < 0) {
            return body;
        }

        int start = body.indexOf('"', marker + 9);
        int end = start < 0 ? -1 : body.indexOf('"', start + 1);
        return start < 0 || end < 0 ? body : body.substring(start + 1, end);
    }

    /** Answer to a catalogue publish. */
    public record PublishResponse(int published, int received) {
    }

    /**
     * Answer to an execution request.
     *
     * <p>{@code plan} and {@code dispatch} are passed through as maps: their shape is the
     * MCP server's contract, and mirroring it into records here would create a second
     * definition of the same thing that has to be kept in step by hand.
     */
    public record ExecutionResult(String status,
                                  Map<String, Object> plan,
                                  Map<String, Object> dispatch) {
    }
}
