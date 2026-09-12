package com.mcpgateway.validation.validator;

import com.mcpgateway.domain.enums.ActionKind;
import com.mcpgateway.domain.json.ActionConfig;
import com.mcpgateway.dto.request.ActionRequest;
import com.mcpgateway.validation.RequestRuleValidator;
import com.mcpgateway.validation.RuleViolations;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * Per-action rules, split by action kind.
 *
 * <p>Reachable two ways: directly, when an action is submitted on its own, and through
 * {@link DefinitionRuleValidator}, which passes a path prefix so violations point at the
 * right element of the actions array.
 */
@Component
public class ActionRuleValidator implements RequestRuleValidator<ActionRequest> {

    private static final Set<String> HTTP_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> TARGET_MODES = Set.of("single", "list", "group");
    private static final Set<String> STRATEGIES = Set.of("sequential", "parallel", "rolling");
    private static final Set<String> SSH_AUTH = Set.of("key", "password", "agent");
    private static final Set<String> MODES = Set.of("static", "dynamic");
    private static final Set<String> DB_ENGINES = Set.of("postgres", "mysql", "mssql", "sqlite", "mongodb");
    private static final Set<String> SQL_OPERATIONS = Set.of("select", "insert", "update", "delete");

    @Override
    public Class<ActionRequest> supportedType() {
        return ActionRequest.class;
    }

    @Override
    public void validate(ActionRequest request, RuleViolations violations) {
        validateAt(request, "action", violations);
    }

    /** Same rules, but reporting under the caller supplied path. */
    public void validateAt(ActionRequest request, String path, RuleViolations violations) {
        ActionConfig config = request.config();

        if (config == null || request.kind() == null) {
            return;
        }
        switch (request.kind()) {
            case REST -> validateRest(config, path, violations);
            case SSH -> validateSsh(request, config, path, violations);
            case DB -> validateDb(config, path, violations);
        }
    }

    /* -------------------------------- REST -------------------------------- */

    private void validateRest(ActionConfig config, String path, RuleViolations violations) {
        violations.addIf(!StringUtils.hasText(config.getUrl()), path + ".config.url",
                "A REST action needs a url");

        violations.addIf(config.getMethod() == null || !HTTP_METHODS.contains(config.getMethod()),
                path + ".config.method", "method must be one of " + HTTP_METHODS);

        // A GET carries no body; allowing one would be silently dropped at call time.
        violations.addIf("GET".equals(config.getMethod()) && StringUtils.hasText(config.getBody()),
                path + ".config.body", "A GET request cannot carry a body");

        violations.addIf(config.getTimeoutMs() != null
                        && (config.getTimeoutMs() < 100 || config.getTimeoutMs() > 600_000),
                path + ".config.timeoutMs", "timeoutMs must be between 100 and 600000");
    }

    /* --------------------------------- SSH --------------------------------- */

    private void validateSsh(ActionRequest request, ActionConfig config, String path, RuleViolations violations) {
        validateSshTarget(request, config, path, violations);
        validateSshCredentials(config, path, violations);
        validateSshCommand(config, path, violations);
    }

    private void validateSshTarget(ActionRequest request, ActionConfig config,
                                   String path, RuleViolations violations) {
        String mode = config.getTargetMode();

        if (mode == null || !TARGET_MODES.contains(mode)) {
            violations.add(path + ".config.targetMode", "targetMode must be one of " + TARGET_MODES);
            return;
        }
        switch (mode) {
            case "single" -> violations.addIf(!StringUtils.hasText(config.getHost()),
                    path + ".config.host", "A single target needs a host address");
            case "list" -> violations.addIf(isEmpty(config.getHosts()),
                    path + ".config.hosts", "A host list target needs at least one address");
            case "group" -> violations.addIf(request.hostGroupId() == null,
                    path + ".hostGroupId", "A group target needs a host group");
            default -> { /* unreachable, the set check above already rejected it */ }
        }

        violations.addIf(config.getStrategy() != null && !STRATEGIES.contains(config.getStrategy()),
                path + ".config.strategy", "strategy must be one of " + STRATEGIES);

        violations.addIf(config.getConcurrency() != null && config.getConcurrency() < 1,
                path + ".config.concurrency", "concurrency must be at least 1");

        violations.addIf(config.getBatchSize() != null && config.getBatchSize() < 1,
                path + ".config.batchSize", "batchSize must be at least 1");
    }

    private void validateSshCredentials(ActionConfig config, String path, RuleViolations violations) {
        String auth = config.getAuth();

        if (auth == null || !SSH_AUTH.contains(auth)) {
            violations.add(path + ".config.auth", "auth must be one of " + SSH_AUTH);
            return;
        }

        // Each secret may come from a stored secret or from a dynamic input, never both.
        violations.addIf(config.getPrivateKeySecretId() != null && config.getPrivateKeyInputKey() != null,
                path + ".config.privateKey",
                "Provide either privateKeySecretId or privateKeyInputKey, not both");

        violations.addIf(config.getPassphraseSecretId() != null && config.getPassphraseInputKey() != null,
                path + ".config.passphrase",
                "Provide either passphraseSecretId or passphraseInputKey, not both");

        violations.addIf(config.getPasswordSecretId() != null && config.getPasswordInputKey() != null,
                path + ".config.password",
                "Provide either passwordSecretId or passwordInputKey, not both");

        if ("agent".equals(auth)) {
            boolean anyCredential = config.getPrivateKeySecretId() != null
                    || config.getPrivateKeyInputKey() != null
                    || config.getPasswordSecretId() != null
                    || config.getPasswordInputKey() != null;

            violations.addIf(anyCredential, path + ".config.auth",
                    "Agent authentication takes no credential fields");
        }
    }

    private void validateSshCommand(ActionConfig config, String path, RuleViolations violations) {
        String mode = config.getCommandMode();

        if (mode == null || !MODES.contains(mode)) {
            violations.add(path + ".config.commandMode", "commandMode must be one of " + MODES);
            return;
        }
        if ("static".equals(mode)) {
            violations.addIf(!StringUtils.hasText(config.getCommand()),
                    path + ".config.command", "A static command cannot be empty");
        } else {
            // Without an allow list the model could propose any command at all.
            violations.addIf(isEmpty(config.getAllowedCommands()),
                    path + ".config.allowedCommands",
                    "A dynamic command needs at least one allowed prefix");
        }
    }

    /* ------------------------------- Database ------------------------------- */

    private void validateDb(ActionConfig config, String path, RuleViolations violations) {
        violations.addIf(config.getEngine() == null || !DB_ENGINES.contains(config.getEngine()),
                path + ".config.engine", "engine must be one of " + DB_ENGINES);

        violations.addIf(!"sqlite".equals(config.getEngine()) && !StringUtils.hasText(config.getHost()),
                path + ".config.host", "Every engine except sqlite needs a host");

        String mode = config.getQueryMode();

        if (mode == null || !MODES.contains(mode)) {
            violations.add(path + ".config.queryMode", "queryMode must be one of " + MODES);
            return;
        }
        if ("static".equals(mode)) {
            violations.addIf(!StringUtils.hasText(config.getQuery()),
                    path + ".config.query", "A static query cannot be empty");
        } else {
            validateDynamicQuery(config, path, violations);
        }
    }

    private void validateDynamicQuery(ActionConfig config, String path, RuleViolations violations) {
        List<String> operations = config.getAllowedOperations();

        if (isEmpty(operations)) {
            violations.add(path + ".config.allowedOperations",
                    "A dynamic query needs at least one permitted statement");
            return;
        }
        operations.stream()
                .filter(operation -> !SQL_OPERATIONS.contains(operation))
                .forEach(operation -> violations.add(path + ".config.allowedOperations",
                        "Unknown statement type: " + operation));

        // Read only and write statements cannot both be true; the panel locks the
        // checkboxes, but the gateway must not depend on the panel for that.
        if (Boolean.TRUE.equals(config.getReadOnly())) {
            boolean hasWrite = operations.stream().anyMatch(operation -> !"select".equals(operation));
            violations.addIf(hasWrite, path + ".config.allowedOperations",
                    "A read only action may only permit select");
        }

        violations.addIf(config.getMaxRows() != null && config.getMaxRows() < 1,
                path + ".config.maxRows", "maxRows must be at least 1");
    }

    private boolean isEmpty(List<String> values) {
        return values == null || values.isEmpty();
    }
}
