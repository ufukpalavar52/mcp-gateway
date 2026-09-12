package com.mcpgateway.queue;

import com.mcpgateway.domain.entity.Action;
import com.mcpgateway.domain.entity.Secret;
import com.mcpgateway.domain.json.ActionConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends a schema-reading job to the executors.
 *
 * <p>Built here rather than by the planner, because there is nothing to plan: the query is
 * this service's own and the model is not involved. It travels on the same queue as
 * ordinary work so there is one path to the executors rather than two.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntrospectionPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(String runRef, String actionRef, Action action, Secret password) {
        ActionConfig config = action.getConfig();

        Map<String, Object> introspect = new HashMap<>();
        introspect.put("engine", config.getEngine());
        introspect.put("host", config.getHost());
        introspect.put("port", config.getPort() == null ? 5432 : config.getPort());
        introspect.put("database", config.getDatabase());
        introspect.put("user", config.getUser());
        introspect.put("tables", config.getSchemaTables());

        if (password != null) {
            // Sealed, like every other credential: this service cannot read it and neither
            // can the queue it travels on.
            introspect.put("password", Map.of(
                    "ciphertext", password.getCiphertext(),
                    "keyId", password.getKeyId(),
                    "context", password.getKind().wireValue()));
        }

        Map<String, Object> job = new HashMap<>();
        job.put("runId", runRef);
        job.put("toolName", "__schema__");
        job.put("definitionId", action.getDefinition() == null ? 0 : action.getDefinition().getId());
        job.put("actor", "schema");
        job.put("dispatchedAt", Instant.now().toString());
        job.put("actions", List.of(Map.of(
                "actionRunId", actionRef,
                "actionId", action.getId(),
                "name", "Read schema",
                "kind", "introspect",
                "introspect", introspect)));

        rabbitTemplate.convertAndSend(QueueNames.ACTIONS, job);
        log.info("Introspection job {} published for action {}", runRef, action.getId());
    }
}
