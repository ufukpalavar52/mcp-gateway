package com.mcpgateway.domain.converter;

import com.mcpgateway.domain.enums.RunStatus;
import jakarta.persistence.Converter;

/** Maps {@link RunStatus} to its PostgreSQL enum label. */
@Converter(autoApply = true)
public class RunStatusConverter extends LowerCaseEnumConverter<RunStatus> {

    public RunStatusConverter() {
        super(RunStatus.class);
    }
}
