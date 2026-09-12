package com.mcpgateway.domain;

import com.mcpgateway.domain.json.ActionConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the copy semantics that keep a duplicated definition independent of its source.
 */
class ActionConfigTest {

    private ActionConfig sample() {
        return ActionConfig.builder()
                .targetMode("list")
                .hosts(new ArrayList<>(List.of("web-01", "web-02")))
                .commandMode("dynamic")
                .allowedCommands(new ArrayList<>(List.of("systemctl status")))
                .headers(new ArrayList<>(List.of(
                        new ActionConfig.HttpHeaderEntry("Authorization", "Bearer x"))))
                .build();
    }

    @Test
    void copyCarriesTheSameValues() {
        ActionConfig original = sample();

        assertThat(original.copy()).usingRecursiveComparison().isEqualTo(original);
    }

    @Test
    void copyDoesNotShareTheNestedLists() {
        ActionConfig original = sample();
        ActionConfig copy = original.copy();

        copy.getHosts().add("web-03");
        copy.getAllowedCommands().clear();

        assertThat(original.getHosts()).containsExactly("web-01", "web-02");
        assertThat(original.getAllowedCommands()).containsExactly("systemctl status");
    }

    @Test
    void copyDoesNotShareTheHeaderEntries() {
        ActionConfig original = sample();
        ActionConfig copy = original.copy();

        copy.getHeaders().get(0).setValue("Bearer tampered");

        assertThat(original.getHeaders().get(0).getValue()).isEqualTo("Bearer x");
    }

    @Test
    void resolveTargetCountFollowsTheTargetMode() {
        assertThat(ActionConfig.builder().targetMode("group").build().resolveTargetCount(10))
                .isEqualTo(10);
        assertThat(sample().resolveTargetCount(0)).isEqualTo(2);
        assertThat(ActionConfig.builder().targetMode("single").host("app-01").build()
                .resolveTargetCount(0)).isEqualTo(1);
        assertThat(ActionConfig.builder().targetMode("single").host("  ").build()
                .resolveTargetCount(0)).isZero();
    }

    /**
     * A config document that omits a collection must still copy.
     *
     * <p>Lombok's @Builder.Default moves the initializer into a static method, so a field
     * is null whenever the object is built any other way — and Jackson builds it through
     * the no-args constructor. A copy that assumed non-null failed on every definition
     * whose action simply did not mention host keys.
     */
    @Test
    void copyToleratesAnOmittedCollection() {
        ActionConfig config = new ActionConfig();
        config.setHostKeys(null);
        config.setHosts(null);

        ActionConfig copy = config.copy();

        assertThat(copy.getHostKeys()).isEmpty();
        assertThat(copy.getHosts()).isEmpty();
    }
}
