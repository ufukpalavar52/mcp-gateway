package com.mcpgateway.event;

/**
 * Something changed that alters which tools the MCP server should expose.
 *
 * <p>An event rather than a direct call so the definition service keeps knowing nothing
 * about the MCP server: it owns definitions, and whether anything downstream cares is
 * not its concern. It also lets the republish wait for the commit, so the MCP server is
 * never handed a catalogue that a rollback then undoes.
 *
 * @param reason what changed, for the log line only
 */
public record CatalogueChangedEvent(String reason) {
}
