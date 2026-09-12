package com.mcpgateway.config;

import com.mcpgateway.domain.enums.LogLevel;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Reads a log level out of a query parameter.
 *
 * <p>The enum's wire form is lower case — that is what the database stores, what the JSON
 * carries and what {@code /logs/counts} answers with. A query parameter went through
 * Spring's default enum conversion instead, which matches constant names, so the panel's
 * own {@code ?level=error} was rejected with a 400 while {@code ERROR} it never sends
 * worked. One contract, read the same way in both directions.
 */
@Component
public class LogLevelConverter implements Converter<String, LogLevel> {

    @Override
    public LogLevel convert(String source) {
        // Blank rather than absent: an unset select still sends the parameter, and an
        // empty one means "no filter", not "no such level".
        return source == null || source.isBlank() ? null : LogLevel.fromWireValue(source.trim());
    }
}
