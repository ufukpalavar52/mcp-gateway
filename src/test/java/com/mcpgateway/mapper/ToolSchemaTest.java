package com.mcpgateway.mapper;

import com.mcpgateway.domain.entity.Definition;
import com.mcpgateway.domain.json.DefinitionInput;
import com.mcpgateway.domain.json.InputType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The JSON Schema hint that decides how an input is drawn.
 *
 * <p>The panel generates its run form from this schema and nothing else, so an input whose
 * shape the schema does not carry is an input the panel cannot draw correctly. mcp-server
 * builds the same schema for real MCP clients from its own table; the two are written
 * separately and must agree.
 */
class ToolSchemaTest {

    /**
     * A file's contents cannot be typed into a one-line field.
     *
     * <p>Both of these used to return {@code null} here and reach the run screen as a plain
     * string, where they were drawn as a single line — which made a definition built around
     * a heredoc impossible to call from the panel that created it.
     */
    @Test
    void aBlockAsksForABox() {
        assertThat(InputType.BLOCK.jsonSchemaFormat()).isEqualTo("textarea");
    }

    @Test
    void aTextareaAsksForABoxToo() {
        assertThat(InputType.TEXTAREA.jsonSchemaFormat()).isEqualTo("textarea");
    }

    /**
     * Same hint, different rules.
     *
     * <p>What separates BLOCK from TEXTAREA is the shell-metacharacter scan the first is
     * exempt from, and that is read from this enum. It is deliberately not in the schema:
     * the schema travels through a catalogue, and a format any caller can write is no place
     * to keep a safety decision. Their hints matching is the point, not an oversight.
     */
    @Test
    void theHintSaysNothingAboutTheScan() {
        assertThat(InputType.BLOCK.jsonSchemaFormat())
                .isEqualTo(InputType.TEXTAREA.jsonSchemaFormat());
    }

    @Test
    void bothStayStrings() {
        assertThat(InputType.BLOCK.jsonSchemaType()).isEqualTo("string");
        assertThat(InputType.TEXTAREA.jsonSchemaType()).isEqualTo("string");
    }

    @Test
    void plainTextStaysALine() {
        assertThat(InputType.TEXT.jsonSchemaFormat()).isNull();
    }

    @Test
    void theOtherHintsAreUntouched() {
        assertThat(InputType.DATE.jsonSchemaFormat()).isEqualTo("date");
        assertThat(InputType.PASSWORD.jsonSchemaFormat()).isEqualTo("password");
    }

    /**
     * A block says how much it will take; nothing else needs to.
     *
     * <p>Every other input is bounded by being a word. This number is advisory — mcp-server
     * enforces the same ceiling where every caller passes — but a client that knows it can
     * refuse while the text is still in front of whoever wrote it, rather than after a
     * quarter of a megabyte has crossed the network.
     */
    @Test
    void aBlockPublishesItsCeiling() {
        Map<String, Object> property = schemaFor(InputType.BLOCK);

        assertThat(property.get("maxLength")).isEqualTo(256 * 1024);
    }

    /**
     * Here the two part company.
     *
     * <p>A textarea is an ordinary value drawn in a bigger box; it keeps the
     * shell-metacharacter scan, and nothing about it is unbounded the way a file's contents
     * are. Giving it the same ceiling would invent a rule nobody asked for.
     */
    @Test
    void aTextareaPublishesNoCeiling() {
        assertThat(schemaFor(InputType.TEXTAREA)).doesNotContainKey("maxLength");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> schemaFor(InputType type) {
        Definition definition = Definition.builder()
                .toolName("t")
                .toolDescription("d")
                .inputs(List.of(DefinitionInput.builder().key("v").type(type).build()))
                .build();

        Map<String, Object> schema = new ToolMapper().toResponse(definition).inputSchema();

        return (Map<String, Object>) ((Map<String, Object>) schema.get("properties")).get("v");
    }
}
