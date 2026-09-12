package com.mcpgateway.domain.converter;

import com.mcpgateway.domain.enums.TargetStatus;
import jakarta.persistence.Converter;

/** Maps {@link TargetStatus} to its PostgreSQL enum label. */
@Converter(autoApply = true)
public class TargetStatusConverter extends LowerCaseEnumConverter<TargetStatus> {

    public TargetStatusConverter() {
        super(TargetStatus.class);
    }
}
