package com.mcpgateway.mapper;

import com.mcpgateway.domain.entity.Definition;
import com.mcpgateway.domain.json.DefinitionInput;
import com.mcpgateway.domain.json.InputSource;
import com.mcpgateway.domain.json.InputType;
import com.mcpgateway.dto.response.ToolResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a definition into an MCP tool descriptor.
 *
 * <p>The JSON Schema is derived on every read rather than stored, so it can never drift
 * from the definition's inputs. Building it here rather than in the service keeps the
 * service about orchestration and leaves all shaping in the mapper layer.
 */
@Component
public class ToolMapper {

    /**
     * The most text one block input may carry, in characters.
     *
     * <p>Every other input is bounded by being a word; a block is bounded by nothing, and
     * it is copied into a command line, a queue message and a shell's stdin on the way to a
     * server. This is the advisory half: mcp-cipher never sees it and mcp-action never
     * checks it, so the enforcement lives in mcp-server's guardrails, where every caller
     * passes through. Keep the two in step.
     */
    private static final int BODY_LIMIT = 256 * 1024;

    public ToolResponse toResponse(Definition definition) {
        return new ToolResponse(
                definition.getToolName(),
                definition.getToolDescription(),
                buildInputSchema(definition.getInputs()),
                definition.getId(),
                definition.getModel() == null ? null : definition.getModel().getModelId(),
                definition.getActions().size(),
                definition.isEnabled());
    }

    /** Translates dynamic inputs into a JSON Schema object. */
    private Map<String, Object> buildInputSchema(List<DefinitionInput> inputs) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (DefinitionInput input : inputs) {
            if (input.getKey() == null || input.getKey().isBlank()) {
                continue;
            }

            // A fixed input is not part of the contract. Publishing it would invite a
            // client to send a value that is then ignored, and the client has no way to
            // tell the difference. mcp-server drops it from its own schema for the same
            // reason; the two must agree or the panel and the MCP client disagree about
            // what a tool takes.
            if (input.getSource() == InputSource.FIXED) {
                continue;
            }

            properties.put(input.getKey(), buildProperty(input));

            if (input.isRequired()) {
                required.add(input.getKey());
            }
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private Map<String, Object> buildProperty(DefinitionInput input) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", input.getType().jsonSchemaType());

        String format = input.getType().jsonSchemaFormat();
        if (format != null) {
            property.put("format", format);
        }
        // Published so a client can refuse before sending rather than after. mcp-server
        // enforces the same ceiling when the command is built — a schema is a description,
        // not a gate — but a caller that knows it can say so while the text is still in
        // front of whoever wrote it. The two numbers must match.
        if (input.getType() == InputType.BLOCK) {
            property.put("maxLength", BODY_LIMIT);
        }
        if (input.getType() == InputType.SELECT
                && input.getOptions() != null && !input.getOptions().isEmpty()) {
            property.put("enum", List.copyOf(input.getOptions()));
        }
        if (input.getLabel() != null && !input.getLabel().isBlank()) {
            property.put("description", input.getLabel());
        }
        // A password input contributes its type and format but never a default value,
        // so a secret cannot leak through the catalogue.
        if (input.getType() != InputType.PASSWORD
                && input.getDefaultValue() != null && !input.getDefaultValue().isBlank()) {
            property.put("default", coerceDefault(input));
        }
        return property;
    }

    /** Keeps the schema's default in the same JSON type as the property it belongs to. */
    private Object coerceDefault(DefinitionInput input) {
        String raw = input.getDefaultValue();

        return switch (input.getType()) {
            case NUMBER -> new BigDecimal(raw);
            case BOOLEAN -> Boolean.parseBoolean(raw);
            default -> raw;
        };
    }
}
