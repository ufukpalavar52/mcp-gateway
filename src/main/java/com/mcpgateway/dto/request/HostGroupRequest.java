package com.mcpgateway.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Create or update payload for a host group. */
public record HostGroupRequest(

        @NotBlank(message = "Group name is required")
        @Size(max = 120, message = "Group name must be at most 120 characters")
        String name,

        String description,

        /** Plain address list; duplicates and blanks are removed by the service. */
        List<String> hosts) {
}
