package com.mcpgateway.dto.response;

import com.mcpgateway.domain.enums.ActionKind;
import com.mcpgateway.domain.json.ActionConfig;

import java.util.List;
import java.util.Map;

/**
 * One action of a definition, resolved far enough to be carried out.
 *
 * <p>The last three fields are what turn a description into something executable, and they
 * exist because only this service can supply them: it owns the host groups and the secret
 * store. Without them the planner can say what would happen but not make it happen — its
 * targets would read "&lt;2 host(s) in web-tier&gt;" and its credentials would be absent.
 *
 * @param hosts every host this action would reach, expanded from the group
 * @param hostKeys expected SSH host key per host; an executor refuses a host it lacks one for
 * @param credentials sealed secrets by role — {@code privateKey}, {@code passphrase},
 *                    {@code password} — which no service between here and the target can read
 */
public record ActionResponse(Long id,
                             ActionKind kind,
                             String name,
                             String description,
                             int position,
                             ActionConfig config,
                             Long hostGroupId,
                             String hostGroupName,
                             int resolvedTargetCount,
                             List<String> hosts,
                             Map<String, String> hostKeys,
                             Map<String, SealedSecretResponse> credentials) {
}
