package com.mcpgateway.validation.validator;

import com.mcpgateway.domain.json.InputType;
import com.mcpgateway.dto.request.ActionRequest;
import com.mcpgateway.dto.request.DefinitionInputRequest;
import com.mcpgateway.dto.request.DefinitionRequest;
import com.mcpgateway.validation.RequestRuleValidator;
import com.mcpgateway.validation.RuleViolations;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Rules that span the whole definition document.
 *
 * <p>These were CHECK constraints until the schema moved actions and inputs into JSONB.
 * They are re-implemented here because the panel's client side validation is a user
 * experience feature, not an authority: the gateway is the only writer and therefore
 * the only place where a rule can actually be enforced.
 *
 * <p>Per-action rules are delegated to {@link ActionRuleValidator}, so this class stays
 * responsible for the document level concerns only.
 */
@Component
@RequiredArgsConstructor
public class DefinitionRuleValidator implements RequestRuleValidator<DefinitionRequest> {

    /** Placeholder keys are embedded into templates, so their shape is restricted. */
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z_][a-z0-9_]*$");
    private static final Pattern TOOL_NAME_PATTERN = Pattern.compile("^[a-z_][a-z0-9_]*$");

    private final ActionRuleValidator actionRuleValidator;

    @Override
    public Class<DefinitionRequest> supportedType() {
        return DefinitionRequest.class;
    }

    @Override
    public void validate(DefinitionRequest request, RuleViolations violations) {
        validateToolName(request.toolName(), violations);
        validateInputs(request.inputs(), violations);
        validateActions(request.actions(), violations);
    }

    private void validateToolName(String toolName, RuleViolations violations) {
        violations.addIf(toolName != null && !TOOL_NAME_PATTERN.matcher(toolName).matches(),
                "toolName", "Tool name must match ^[a-z_][a-z0-9_]*$");
    }

    private void validateInputs(List<DefinitionInputRequest> inputs, RuleViolations violations) {
        if (inputs == null) {
            return;
        }
        Set<String> seenKeys = new HashSet<>();

        for (int index = 0; index < inputs.size(); index++) {
            DefinitionInputRequest input = inputs.get(index);
            String path = "inputs[" + index + "]";

            if (input.key() != null && !KEY_PATTERN.matcher(input.key()).matches()) {
                violations.add(path + ".key", "Key must match ^[a-z_][a-z0-9_]*$");
            }
            if (input.key() != null && !seenKeys.add(input.key())) {
                violations.add(path + ".key", "Duplicate input key: " + input.key());
            }

            // A secret must never be persisted as part of the definition document.
            if (input.type() == InputType.PASSWORD
                    && input.defaultValue() != null && !input.defaultValue().isBlank()) {
                violations.add(path + ".defaultValue",
                        "A password input cannot carry a default value");
            }

            if (input.type() == InputType.SELECT
                    && (input.options() == null || input.options().isEmpty())) {
                violations.add(path + ".options", "A select input needs at least one option");
            }
        }
    }

    private void validateActions(List<ActionRequest> actions, RuleViolations violations) {
        if (actions == null) {
            return;
        }
        for (int index = 0; index < actions.size(); index++) {
            actionRuleValidator.validateAt(actions.get(index), "actions[" + index + "]", violations);
        }
    }
}
