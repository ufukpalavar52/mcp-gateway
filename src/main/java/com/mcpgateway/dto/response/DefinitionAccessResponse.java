package com.mcpgateway.dto.response;

import com.mcpgateway.domain.enums.DefinitionAccess;

import java.util.List;

/** Who may reach a definition, as the access screen reads it. */
public record DefinitionAccessResponse(DefinitionAccess access, List<Grant> permissions) {

    public record Grant(Long userId, String email, String fullName,
                        boolean canRun, boolean canEdit) {
    }
}
