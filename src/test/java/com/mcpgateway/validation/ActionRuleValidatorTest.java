package com.mcpgateway.validation;

import com.mcpgateway.domain.enums.ActionKind;
import com.mcpgateway.domain.json.ActionConfig;
import com.mcpgateway.dto.request.ActionRequest;
import com.mcpgateway.validation.validator.ActionRuleValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the rules that stopped being database constraints when actions moved into
 * JSONB. Pure unit tests: no Spring context and no infrastructure.
 */
class ActionRuleValidatorTest {

    private final ActionRuleValidator validator = new ActionRuleValidator();

    private RuleViolations validate(ActionKind kind, ActionConfig config, Long hostGroupId) {
        RuleViolations violations = new RuleViolations();
        // Credentials are null throughout: these rules are about the config's shape, and a
        // credential is neither part of it nor validated by it.
        validator.validate(
                new ActionRequest(null, kind, "test", "", config, hostGroupId, null, null, null),
                violations);
        return violations;
    }

    @Nested
    @DisplayName("REST actions")
    class RestActions {

        @Test
        void acceptsAWellFormedRequest() {
            ActionConfig config = ActionConfig.builder()
                    .method("POST").url("https://ci.internal/deploy")
                    .body("{}").timeoutMs(15_000)
                    .build();

            assertThat(validate(ActionKind.REST, config, null).isEmpty()).isTrue();
        }

        @Test
        void rejectsABodyOnGet() {
            ActionConfig config = ActionConfig.builder()
                    .method("GET").url("https://ci.internal/status").body("{\"a\":1}")
                    .build();

            assertThat(validate(ActionKind.REST, config, null).describe())
                    .contains("GET request cannot carry a body");
        }

        @Test
        void rejectsAnUnknownMethod() {
            ActionConfig config = ActionConfig.builder().method("FETCH").url("https://x").build();

            assertThat(validate(ActionKind.REST, config, null).describe()).contains("method must be one of");
        }
    }

    @Nested
    @DisplayName("SSH actions")
    class SshActions {

        private ActionConfig.ActionConfigBuilder baseSsh() {
            return ActionConfig.builder()
                    .targetMode("single").host("app-01.internal")
                    .auth("key").commandMode("static").command("uptime");
        }

        @Test
        void acceptsAWellFormedRequest() {
            assertThat(validate(ActionKind.SSH, baseSsh().build(), null).isEmpty()).isTrue();
        }

        @Test
        void requiresAHostForASingleTarget() {
            ActionConfig config = baseSsh().host("").build();

            assertThat(validate(ActionKind.SSH, config, null).describe())
                    .contains("single target needs a host address");
        }

        @Test
        void requiresAGroupWhenTargetModeIsGroup() {
            ActionConfig config = baseSsh().targetMode("group").host("").build();

            assertThat(validate(ActionKind.SSH, config, null).describe())
                    .contains("group target needs a host group");
        }

        @Test
        void acceptsAGroupTargetWhenTheGroupIsGiven() {
            ActionConfig config = baseSsh().targetMode("group").host("").build();

            assertThat(validate(ActionKind.SSH, config, 7L).isEmpty()).isTrue();
        }

        @Test
        void requiresACommandInStaticMode() {
            ActionConfig config = baseSsh().command("").build();

            assertThat(validate(ActionKind.SSH, config, null).describe())
                    .contains("static command cannot be empty");
        }

        @Test
        void requiresAnAllowListInDynamicMode() {
            ActionConfig config = baseSsh().commandMode("dynamic").command("").build();

            assertThat(validate(ActionKind.SSH, config, null).describe())
                    .contains("needs at least one allowed prefix");
        }

        @Test
        void rejectsTwoSourcesForTheSameSecret() {
            ActionConfig config = baseSsh()
                    .privateKeySecretId(4L).privateKeyInputKey("ssh_key")
                    .build();

            assertThat(validate(ActionKind.SSH, config, null).describe())
                    .contains("not both");
        }

        @Test
        void rejectsCredentialsWhenAuthenticatingThroughTheAgent() {
            ActionConfig config = baseSsh().auth("agent").privateKeyInputKey("ssh_key").build();

            assertThat(validate(ActionKind.SSH, config, null).describe())
                    .contains("Agent authentication takes no credential fields");
        }
    }

    @Nested
    @DisplayName("Database actions")
    class DatabaseActions {

        private ActionConfig.ActionConfigBuilder baseDb() {
            return ActionConfig.builder()
                    .engine("postgres").host("db.internal").database("metrics")
                    .queryMode("static").query("select 1");
        }

        @Test
        void acceptsAWellFormedRequest() {
            assertThat(validate(ActionKind.DB, baseDb().build(), null).isEmpty()).isTrue();
        }

        @Test
        void allowsSqliteWithoutAHost() {
            ActionConfig config = baseDb().engine("sqlite").host("").database("/var/data/app.db").build();

            assertThat(validate(ActionKind.DB, config, null).isEmpty()).isTrue();
        }

        @Test
        void requiresAQueryInStaticMode() {
            ActionConfig config = baseDb().query("").build();

            assertThat(validate(ActionKind.DB, config, null).describe())
                    .contains("static query cannot be empty");
        }

        @Test
        void requiresPermittedStatementsInDynamicMode() {
            ActionConfig config = baseDb().queryMode("dynamic").query("")
                    .allowedOperations(List.of()).build();

            assertThat(validate(ActionKind.DB, config, null).describe())
                    .contains("needs at least one permitted statement");
        }

        @Test
        void rejectsWriteStatementsWhileReadOnly() {
            ActionConfig config = baseDb().queryMode("dynamic").query("")
                    .allowedOperations(List.of("select", "update")).readOnly(true).build();

            assertThat(validate(ActionKind.DB, config, null).describe())
                    .contains("read only action may only permit select");
        }

        @Test
        void allowsWriteStatementsWhenNotReadOnly() {
            ActionConfig config = baseDb().queryMode("dynamic").query("")
                    .allowedOperations(List.of("select", "update")).readOnly(false).build();

            assertThat(validate(ActionKind.DB, config, null).isEmpty()).isTrue();
        }
    }
}
