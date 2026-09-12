package com.mcpgateway.domain.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Type-specific settings of an action, stored in {@code actions.config}.
 *
 * <p>The three action kinds share one class on purpose: the column holds a single
 * JSON document and Jackson would otherwise need a discriminator inside the JSON.
 * Which subset of fields is meaningful is decided by {@code actions.kind}, and the
 * cross-field rules are enforced by the validation layer rather than by the type.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActionConfig {

    /* ------------------------------- REST ------------------------------- */

    /** GET | POST | PUT | PATCH | DELETE */
    private String method;
    private String url;
    @Builder.Default
    private List<HttpHeaderEntry> headers = new ArrayList<>();
    private String body;
    private Integer timeoutMs;

    /* -------------------------------- SSH -------------------------------- */

    /** single | list | group — for {@code group} the target lives in the column. */
    private String targetMode;
    private String host;
    @Builder.Default
    private List<String> hosts = new ArrayList<>();
    /** sequential | parallel | rolling */
    private String strategy;
    private Integer concurrency;
    private Integer batchSize;
    private Boolean stopOnError;

    private Integer port;
    private String user;
    private String workingDir;
    /** key | password | agent */
    private String auth;

    /* Credential references. Exactly one of each pair may be set, never both,
     * and a plaintext key or password is never stored. */
    /**
     * Expected SSH host key per host, in authorized_keys format.
     *
     * <p>Lives in this JSON document rather than beside the host names because the
     * document takes a new field without a migration, and because a key is a property of
     * how this action reaches a host rather than of the host group itself.
     *
     * <p>The executor refuses to connect to a host it has no key for. That is deliberate:
     * it authenticates with a private key and then runs whatever it was told, so anyone
     * able to answer for the host would get both.
     */
    @Builder.Default
    private Map<String, String> hostKeys = new HashMap<>();

    private Long privateKeySecretId;
    private String privateKeyInputKey;
    private Long passphraseSecretId;
    private String passphraseInputKey;
    private Long passwordSecretId;
    private String passwordInputKey;

    /** static | dynamic */
    private String commandMode;
    private String command;
    @Builder.Default
    private List<String> allowedCommands = new ArrayList<>();
    @Builder.Default
    private List<String> blockedPatterns = new ArrayList<>();
    private String commandGuidance;
    private Boolean requireApproval;
    private Boolean sudo;

    /**
     * Stream the command's output while it runs, rather than only at the end.
     *
     * <p>Off unless the action asks. What it is for is the commands that do not finish on
     * their own — {@code tail -f}, a long install — where the whole value is in seeing the
     * lines as they arrive.
     */
    private Boolean follow;

    /**
     * How long a followed command may stay quiet before it is stopped, in seconds.
     *
     * <p>Idle rather than total: a log is watched until it goes quiet, not for a fixed
     * span. A fixed minute cuts off a busy log mid-sentence and spends the whole minute on
     * a silent one; every line printed starts this again.
     *
     * <p>Defaulted and capped by the planner, which also sets the ceiling that bounds a log
     * that never stops printing. That ceiling is not here on purpose — it is a property of
     * the executor's capacity, not of what one definition is worth.
     */
    private Integer followIdleSeconds;

    /* ----------------------------- Database ----------------------------- */

    /** postgres | mysql | mssql | sqlite | mongodb */
    private String engine;
    private String database;
    /** static | dynamic */
    private String queryMode;
    private String query;
    private String schemaHint;

    /**
     * Tables the model is told about, and the only ones introspection reads.
     *
     * <p>An allow list, not a filter. A real schema has hundreds of tables and will not fit
     * in a prompt — and a model given all of them picks the wrong one more often than it
     * picks none. Two or three named tables are both cheaper and more accurate.
     */
    @Builder.Default
    private List<String> schemaTables = new ArrayList<>();

    /**
     * The schema as the database actually reports it, and when it was read.
     *
     * <p>Kept apart from {@link #schemaHint}, which is what an operator typed. They answer
     * different questions — one is intent, the other is fact — and overwriting the hint
     * would throw away the notes somebody wrote about what the tables mean.
     */
    private String generatedSchema;

    /**
     * When the schema was read, as an ISO-8601 string.
     *
     * <p>Text rather than {@code Instant}: this document is stored as JSONB through a
     * mapper that has no java-time module, and an {@code Instant} here failed the whole
     * write with "could not serialize" — losing the schema and the run's status with it.
     */
    private String generatedSchemaAt;
    private String guidance;
    /** select | insert | update | delete */
    @Builder.Default
    private List<String> allowedOperations = new ArrayList<>();
    private Integer maxRows;
    private Boolean readOnly;

    /**
     * Independent copy, including the nested collections.
     *
     * <p>This type is mutable and is used both as a request field and as the persisted
     * document. Without a copy the caller's object, the entity and, when a definition is
     * duplicated, two separate definitions would all reference the same instance, so a
     * later edit to one would silently change the others.
     */
    public ActionConfig copy() {
        return ActionConfig.builder()
                .method(method)
                .url(url)
                .headers(headers == null ? new ArrayList<>() : headers.stream()
                        .map(header -> new HttpHeaderEntry(header.getKey(), header.getValue()))
                        .collect(Collectors.toCollection(ArrayList::new)))
                .body(body)
                .timeoutMs(timeoutMs)
                .targetMode(targetMode)
                .host(host)
                .hosts(copyOf(hosts))
                .strategy(strategy)
                .concurrency(concurrency)
                .batchSize(batchSize)
                .stopOnError(stopOnError)
                .port(port)
                .user(user)
                .workingDir(workingDir)
                .auth(auth)
                .hostKeys(copyOf(hostKeys))
                .privateKeySecretId(privateKeySecretId)
                .privateKeyInputKey(privateKeyInputKey)
                .passphraseSecretId(passphraseSecretId)
                .passphraseInputKey(passphraseInputKey)
                .passwordSecretId(passwordSecretId)
                .passwordInputKey(passwordInputKey)
                .commandMode(commandMode)
                .command(command)
                .allowedCommands(copyOf(allowedCommands))
                .blockedPatterns(copyOf(blockedPatterns))
                .commandGuidance(commandGuidance)
                .requireApproval(requireApproval)
                .sudo(sudo)
                .follow(follow)
                .followIdleSeconds(followIdleSeconds)
                .engine(engine)
                .database(database)
                .queryMode(queryMode)
                .query(query)
                .schemaHint(schemaHint)
                .schemaTables(copyOf(schemaTables))
                .generatedSchema(generatedSchema)
                .generatedSchemaAt(generatedSchemaAt)
                .guidance(guidance)
                .allowedOperations(copyOf(allowedOperations))
                .maxRows(maxRows)
                .readOnly(readOnly)
                .build();
    }

    /**
     * Resolves how many hosts this configuration would touch for the given group size.
     *
     * <p>Kept here rather than in a mapper because it is domain knowledge: the same
     * rule decides what the executor fans out to.
     */
    public int resolveTargetCount(int hostGroupSize) {
        if (targetMode == null) {
            return 0;
        }
        return switch (targetMode) {
            case "group" -> hostGroupSize;
            case "list" -> hosts == null ? 0 : hosts.size();
            case "single" -> host == null || host.isBlank() ? 0 : 1;
            default -> 0;
        };
    }

    /*
     * Null tolerant on purpose. @Builder.Default moves a field initializer into a static
     * method, so the field is null whenever the object is built any other way — which
     * Jackson does, through the no-args constructor. Copying without this guard fails on
     * every document that simply omits the collection.
     */
    private List<String> copyOf(List<String> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    private Map<String, String> copyOf(Map<String, String> values) {
        return values == null ? new HashMap<>() : new HashMap<>(values);
    }

    /** One REST header. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HttpHeaderEntry {
        private String key;
        private String value;
    }
}
