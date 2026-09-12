package com.mcpgateway.service.intf;

/** Reading a database's own structure, so the model does not have to be told it by hand. */
public interface SchemaService {

    /**
     * Dispatches an introspection for one database action.
     *
     * <p>Goes through the queue like any other work, because only mcp-action reaches the
     * target database — this service holds the credential but has no route to the machine,
     * and giving it one would undo the reason the executor exists.
     *
     * @return the run reference the answer will arrive against
     */
    String introspect(Long definitionId, Long actionId);
}
