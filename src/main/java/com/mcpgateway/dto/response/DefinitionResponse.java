package com.mcpgateway.dto.response;

import com.mcpgateway.domain.enums.ModelProvider;
import com.mcpgateway.domain.json.ModelParams;
import com.mcpgateway.domain.json.DefinitionInput;

import java.time.Instant;
import java.util.List;

/** Full definition document, including its actions. */
public record DefinitionResponse(Long id,
                                 String name,
                                 String toolName,
                                 String toolDescription,
                                 Long modelId,
                                 String modelName,
                                 String modelIdentifier,
                                 /*
                                  * Which service answers for this model, and where it
                                  * lives. Sent because the MCP server has to pick a
                                  * client, and a model id alone does not say whether
                                  * "llama3" means Ollama on a laptop or a hosted
                                  * endpoint. The API key is deliberately not here: the
                                  * MCP server holds its own credentials per provider.
                                  */
                                 ModelProvider modelProvider,
                                 String modelEndpoint,
                                 /*
                                  * The model's API key, still sealed.
                                  *
                                  * Sent because the planner is what calls the model, and
                                  * until this existed it could not: the key was stored
                                  * against the model here and read from the planner's own
                                  * environment there, so a key entered in the panel did
                                  * nothing for the process that needed it.
                                  *
                                  * Sealed, so carrying it is not a disclosure — the planner
                                  * opens it through mcp-cipher at the moment of use, the
                                  * same way the executor opens an SSH key.
                                  */
                                 SealedSecretResponse modelApiKey,

                                 /**
                                  * The model's request parameters, for the same reason as
                                  * the key above: the panel offers them, this service
                                  * stores them, and the planner is the process that makes
                                  * the request. Without them a temperature set in the
                                  * panel changed nothing at all.
                                  */
                                 ModelParams modelParams,
                                 String systemPrompt,
                                 List<DefinitionInput> inputs,
                                 List<ActionResponse> actions,
                                 boolean enabled,
                                 Instant updatedAt) {
}
