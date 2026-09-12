package com.mcpgateway.config;

import com.mcpgateway.domain.enums.LogLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A level arrives from a query parameter in the same form it leaves in.
 *
 * <p>It did not. {@code /logs/counts} answers with lower case keys, the panel filters with
 * {@code ?level=error}, and Spring's default enum conversion matches constant names — so
 * the panel's own value came back 400 while the upper case it never sends worked.
 */
class LogLevelConverterTest {

    private final LogLevelConverter converter = new LogLevelConverter();

    @Test
    void theWireFormIsAccepted() {
        assertThat(converter.convert("error")).isEqualTo(LogLevel.ERROR);
        assertThat(converter.convert("warn")).isEqualTo(LogLevel.WARN);
        assertThat(converter.convert("info")).isEqualTo(LogLevel.INFO);
    }

    @Test
    void caseAndSpacingDoNotDecideTheAnswer() {
        assertThat(converter.convert("ERROR")).isEqualTo(LogLevel.ERROR);
        assertThat(converter.convert(" Warn ")).isEqualTo(LogLevel.WARN);
    }

    @Test
    void anUnsetFilterIsNoFilter() {
        // A select left on "all" still sends the parameter, empty. That means "everything",
        // not "a level called nothing".
        assertThat(converter.convert("")).isNull();
        assertThat(converter.convert("   ")).isNull();
    }

    @Test
    void somethingThatIsNotALevelIsRejected() {
        // Rather than silently widening to every row, which would answer a wrong question
        // with a plausible looking list.
        assertThatThrownBy(() -> converter.convert("critical"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
