package com.mcpgateway.validation.validator;

import com.mcpgateway.dto.request.HostGroupRequest;
import com.mcpgateway.validation.RequestRuleValidator;
import com.mcpgateway.validation.RuleViolations;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Rejects host lists that would silently lose entries. */
@Component
public class HostGroupRuleValidator implements RequestRuleValidator<HostGroupRequest> {

    @Override
    public Class<HostGroupRequest> supportedType() {
        return HostGroupRequest.class;
    }

    @Override
    public void validate(HostGroupRequest request, RuleViolations violations) {
        List<String> hosts = request.hosts();

        if (hosts == null || hosts.isEmpty()) {
            return;
        }
        Set<String> seen = new HashSet<>();

        for (String host : hosts) {
            String trimmed = host == null ? "" : host.trim();

            if (trimmed.isEmpty()) {
                continue;
            }
            violations.addIf(!seen.add(trimmed), "hosts",
                    "Duplicate host address: " + trimmed);
        }
    }
}
