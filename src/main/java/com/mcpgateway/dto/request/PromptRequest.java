package com.mcpgateway.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A sentence to route to a tool.
 *
 * @param execute whether to dispatch the resulting plan. Defaults to false: deciding what
 *                a sentence means and acting on it are different things, and a console
 *                that did both by default would make the second one invisible.
 */
public record PromptRequest(

        @NotBlank(message = "Prompt is required")
        @Size(max = 8000, message = "Prompt must be at most 8000 characters")
        String prompt,

        Boolean execute,

        /**
         * Which tool to use, when the caller has already decided.
         *
         * <p>Naming one skips the model's choice, not the planning that follows: a
         * definition is written for a particular job, and an operator who knows which job
         * they are doing should not have to hope a model agrees. The guardrails are in the
         * step after, and this does not go near them.
         */
        String toolName,

        /**
         * The console session this question belongs to.
         *
         * <p>Null starts a new one, and so does a reference that is not the caller's — a
         * console should not throw away a typed question because the session behind it was
         * deleted in another tab. The reference comes back in the response either way.
         */
        String conversationRef) {

    public boolean shouldExecute() {
        return Boolean.TRUE.equals(execute);
    }
}
