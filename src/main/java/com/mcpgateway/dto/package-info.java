/**
 * Transport types of the HTTP API.
 *
 * <p>Split by direction: {@code request} carries what clients send, {@code response}
 * what they receive. Both are Java records, so a payload cannot be mutated after it is
 * bound or built.
 *
 * <h2>Why domain enums and JSON payload types appear here</h2>
 *
 * <p>DTOs reference {@code domain.enums} and {@code domain.json} rather than redeclaring
 * them. Those types <em>are</em> the published contract: their shape is documented in
 * {@code docs/DATABASE.md}, the panel sends exactly that JSON, and the Python MCP server
 * reads it back. A parallel set of DTO copies would have to be kept in step by hand for
 * no gain, and a drift between the two would surface as a runtime serialisation bug.
 *
 * <p>The rule this buys is worth stating: changing a {@code domain.json} type is an API
 * change. Treat it as one.
 *
 * <p>JPA entities are never exposed. They carry lazy associations and identity, which
 * belong to persistence rather than to the wire.
 */
package com.mcpgateway.dto;
