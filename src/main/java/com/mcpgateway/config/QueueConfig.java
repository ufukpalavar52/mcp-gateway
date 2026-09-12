package com.mcpgateway.config;

import com.mcpgateway.queue.QueueNames;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The broker topology, declared by this service as well as by the others.
 *
 * <p>Declarations are idempotent, and every participant making them means any one of them
 * can be started first against a fresh broker. The alternative — one service owning the
 * topology — makes start-up order matter for no benefit.
 */
@Configuration
public class QueueConfig {

    @Bean
    Queue actionsQueue() {
        // Durable: a broker restart must not discard work that was accepted.
        return new Queue(QueueNames.ACTIONS, true);
    }

    @Bean
    Queue resultsQueue() {
        return new Queue(QueueNames.RESULTS, true);
    }

    @Bean
    Queue progressQueue() {
        // Not durable, bounded, and its messages expire. A live view is worth having for
        // as long as somebody is looking at it and worth nothing afterwards; these limits
        // are what stop a watcher nobody reads from costing the broker memory that only
        // grows. Declared with the same arguments mcp-action uses, because a mismatch on
        // an existing queue is a channel-level error rather than a warning.
        return QueueBuilder.nonDurable(QueueNames.PROGRESS)
                .withArgument("x-max-length", 2000)
                .withArgument("x-overflow", "drop-head")
                .withArgument("x-message-ttl", 60_000)
                .build();
    }

    @Bean
    FanoutExchange cancellationsExchange() {
        return new FanoutExchange(QueueNames.CANCELLATIONS, true, false);
    }

    /**
     * JSON, matching what the Go and Python sides publish.
     *
     * <p>Spring's default is Java serialization, which nothing else in this stack can read
     * or write.
     *
     * <p>{@code JacksonJsonMessageConverter} rather than {@code Jackson2…}: the unversioned
     * name is the Jackson 3 converter, which is what Boot 4 ships.
     */
    @Bean
    MessageConverter queueMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
