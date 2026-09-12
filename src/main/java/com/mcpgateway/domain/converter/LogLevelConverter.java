package com.mcpgateway.domain.converter;

import com.mcpgateway.domain.enums.LogLevel;
import jakarta.persistence.Converter;

/** Maps {@link LogLevel} to its PostgreSQL enum label. */
@Converter(autoApply = true)
public class LogLevelConverter extends LowerCaseEnumConverter<LogLevel> {

    public LogLevelConverter() {
        super(LogLevel.class);
    }
}
