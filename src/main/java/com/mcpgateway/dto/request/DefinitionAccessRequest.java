package com.mcpgateway.dto.request;

import com.mcpgateway.domain.enums.DefinitionAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Who may reach a definition, replaced wholesale the way its actions are. */
public record DefinitionAccessRequest(

        @NotNull(message = "Access mode is required")
        DefinitionAccess access,

        List<@Valid Grant> permissions) {

    /**
     * One person's standing.
     *
     * <p>{@code canEdit} implies {@code canRun} wherever this is read, so a grant of edit
     * alone is not a contradiction — it is the shorter way of saying both.
     */
    public record Grant(
            @NotNull(message = "A user is required") Long userId,
            boolean canRun,
            boolean canEdit) {
    }
}
