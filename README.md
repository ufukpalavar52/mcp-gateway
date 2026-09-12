# mcp-gateway

Spring Boot backend for the MCP panel. It is the **only writer** of the database, and
the only one of the three services that connects to it at all: the Python MCP server and
the Go executor never touch PostgreSQL. They are given what they need — this service
pushes the tool catalogue to the MCP server and asks it for plans, and the executor will
receive work through the queue.

Schema, JSON payload shapes and design rationale: [`docs/DATABASE.md`](docs/DATABASE.md).
Runnable DDL: [`docs/schema.sql`](docs/schema.sql). The Liquibase changelog lives in
[`db/changelog/`](db/changelog/) and is applied by a **separate container**, not by
this service.

The panel's own documentation (routes, definition model, localisation) stays in the
`mcp-panel` repository under `docs/PANEL.md`.

## The MCP server

The MCP server decides what a tool call resolves to; this service owns the definitions
and the audit trail. The link between them is one way — this service calls out, the MCP
server never calls back, and it holds no credentials for this service.

| Direction | Call |
|---|---|
| out | `PUT /api/v1/catalogue` — replaces the MCP server's tool list wholesale |
| out | `POST /api/v1/executions` — asks what a call resolves to |

The catalogue is **pushed, not fetched**, and replaced rather than merged: a merge would
need a separate protocol for deletions, and a missed removal would leave a deleted
definition callable. The MCP server keeps it in memory, so `CataloguePublisher` pushes on
start up and after every committed definition change. A failed publish is a warning, never
an error — the definition is saved either way, and `POST /api/v1/tools/publish` closes the
gap once the MCP server is back.

An execution returns a **plan**, not a result. Nothing is executed yet: `dispatch.status`
comes back `skipped` because no executor is configured, or `refused` when the plan itself
was rejected. Either way the call is written to `tool_calls`, with a rejected plan recorded
at `error` level so it does not read as ordinary traffic.

Configured under `mcp.server.*`, served by mcp-config.

## Stack

| Concern | Choice |
|---|---|
| Framework | Spring Boot 4.1.1, Java 21 |
| Persistence | Spring Data JPA, PostgreSQL, JSONB via `@JdbcTypeCode(SqlTypes.JSON)` |
| Migrations | Liquibase, run as its own container — the app carries no migration library |
| Sessions | Redis, token id registry |
| Auth | JWT access and refresh tokens, rotation on refresh |
| Validation | Jakarta Bean Validation plus an AOP layer for domain rules |

## Running

The whole stack, including the migration job:

```bash
docker compose up
```

`compose` starts PostgreSQL and Redis, runs the `liquibase` service until it exits
successfully, and only then starts the gateway
(`condition: service_completed_successfully`).

### Running the service alone

The schema must already exist. Apply it first:

```bash
docker compose run --rm liquibase          # or: docker compose up liquibase

# mcp-config must already be running: this service reads its configuration from it,
# credentials included, and refuses to start without it. Nothing else needs setting.
./mvnw spring-boot:run
```

### Schema ownership

**The application performs no schema work at start up.** It carries no migration
library, `ddl-auto` is `none`, and nothing validates the schema on boot. Migrations
are the Liquibase container's job alone.

> The trade-off is deliberate but worth knowing: with `ddl-auto: validate` a drift
> between entities and schema failed loudly at boot. Now it surfaces later, as a
> query error at runtime. Guard against it by running the migration job before every
> deploy — the compose file wires exactly that ordering — and by keeping
> `docs/schema.sql` and the changelog in step.

> The JDBC url carries `stringtype=unspecified`. PostgreSQL enum columns reject a
> bound `varchar`, and this flag lets the driver leave the cast to the server.

## Endpoints

| Method | Path | Role | Purpose |
|---|---|---|---|
| `POST` | `/api/v1/auth/login` | public | Issue a token pair |
| `POST` | `/api/v1/auth/register` | public | Create an account |
| `POST` | `/api/v1/auth/refresh` | public | Rotate the token pair |
| `POST` | `/api/v1/auth/logout` | any | Revoke the current session |
| `POST` | `/api/v1/auth/logout-all` | any | Revoke every session of the caller |
| `GET` | `/api/v1/models` | any | List model connections |
| `POST` `PUT` `DELETE` | `/api/v1/models[/{id}]` | admin | Manage model connections |
| `POST` | `/api/v1/models/{id}/toggle` | admin | Enable or disable |
| `GET` | `/api/v1/definitions` | any | Paged definition summaries |
| `GET` | `/api/v1/definitions/{id}` | any | Full definition with actions |
| `POST` `PUT` `DELETE` | `/api/v1/definitions[/{id}]` | admin, developer | Manage definitions |
| `POST` | `/api/v1/definitions/{id}/duplicate` | admin, developer | Copy a definition |
| `GET` | `/api/v1/host-groups` | any | List host groups |
| `POST` `PUT` `DELETE` | `/api/v1/host-groups[/{id}]` | admin, developer | Manage host groups |
| `GET` | `/api/v1/tools?published=true` | any | Tool catalogue with JSON Schema |
| `GET` | `/api/v1/tools/{toolName}` | any | One tool |
| `POST` | `/api/v1/tools/{toolName}/execute` | admin, developer | Ask the MCP server what the call resolves to |
| `POST` | `/api/v1/tools/publish` | admin | Republish the catalogue to the MCP server |
| `GET` | `/api/v1/users/me` | any | The caller's account |
| `GET` `PUT` | `/api/v1/users[/{id}]` | admin | Account administration |
| `POST` | `/api/v1/users/invitations` | admin | Create an invitation |
| `GET` | `/api/v1/logs` | any | Tool call audit trail |

`GET /api/v1/tools?published=true` serves the panel. The MCP server is not a caller of
it — it is sent the same definitions through `PUT /api/v1/catalogue` and derives the
`inputSchema` itself, so the two cannot drift into disagreeing about one tool's shape.

## Token flow

```
login / register  ──▶  access token (15m) + refresh token (7d)
                        both ids registered in Redis

request           ──▶  JwtAuthenticationFilter
                        1. signature and expiry verify
                        2. token type is access
                        3. token id still registered  ← makes logout immediate

refresh           ──▶  presented refresh token revoked, new pair issued
```

Rotation means a stolen refresh token works at most once, and the theft surfaces the
next time the legitimate client refreshes and is rejected.

Suspending an account calls `revokeAllForUser`, so access ends at once instead of
lasting until the access token expires.

## Package layout

```
com.mcpgateway
├── config/        SecurityConfig, RedisConfig, ApplicationConfig
├── property/      settings classes bound from the served configuration
├── security/      JWT service, Redis token store, authentication filter
├── validation/    rule validators and the AOP aspect that applies them
├── domain/        entity, enums, converter, json payload types
├── repository/
├── service/
│   ├── intf/      service contracts
│   └── impl/      implementations
├── mapper/
├── dto/           request and response records
└── controller/
```

Interfaces and implementations sit in sibling packages. The more common Spring
convention keeps the contracts one level up, in `service/` itself, with only the
implementations in `service/impl/`; both work, and the symmetric form makes the
relationship easier to read at a glance.

## Configuration

**The configuration lives in mcp-config, not here.** This repository's
`application.yml` holds only what the service needs in order to find it:

```yaml
spring:
  application:
    name: mcp-gateway
  config:
    import: configserver:${CONFIG_SERVER_URL:http://127.0.0.1:8888}
```

Everything else — datasource, Redis, `mcp.*` — is served from `config-repo/mcp-gateway.yml`
in that repository. The import is mandatory: without the config server this service fails
fast with a connection error, rather than starting on whatever defaults happened to remain
in a local file.

Credentials are served too, from mcp-config's own untracked `.env`. This service therefore
starts with **nothing** in its environment — no `DB_PASSWORD`, no `JWT_SECRET_KEY` — which
is the point of centralising them. Set one anyway and it wins: the environment outranks the
config server, so a local override needs no central edit.

Values are bound into classes under `com.mcpgateway.property`. `ApplicationConfig`
discovers them with `@ConfigurationPropertiesScan`, so a new settings class needs no
registration.

| Class | Prefix | Holds |
|---|---|---|
| `JwtProperties` | `mcp.jwt` | Signing secret, issuer, access and refresh lifetimes |
| `CorsProperties` | `mcp.cors` | Allowed origins, methods, credentials, preflight max age |
| `InvitationProperties` | `mcp.invitation` | How long an invite link stays valid |

Each is `@Validated`, so a blank secret or an empty origin list fails at start up
rather than at the first request that needs it.

Every key also accepts an environment override, which is how deployments configure
the service:

```yaml
mcp:
  jwt:
    secret: ${JWT_SECRET_KEY:change-me-this-development-secret-is-at-least-32-bytes}
    access-token-ttl: ${JWT_ACCESS_TTL:15m}
```

> The list of public endpoints in `SecurityConfig` is deliberately **not**
> externalised. It is the authentication boundary, and a typo in a configuration file
> must never be able to expose an endpoint.

## Validation

Two layers, deliberately separate:

**Bean validation** checks the shape of a request: required fields, sizes, email
format. Declared on the request records.

**Domain rules** are conditional across fields or need a lookup, so they cannot be
expressed as field constraints. They were CHECK constraints until the schema moved
actions and inputs into JSONB. A method annotated with `@ValidateBusinessRules` is
intercepted by `BusinessRuleValidationAspect`, which resolves a
`RequestRuleValidator` for each argument type and merges every violation into one
`422` response.

Adding a rule set means adding a bean:

```java
@Component
public class MyRuleValidator implements RequestRuleValidator<MyRequest> {

    @Override public Class<MyRequest> supportedType() { return MyRequest.class; }

    @Override public void validate(MyRequest request, RuleViolations violations) {
        violations.addIf(condition, "field", "message");
    }
}
```

The aspect never changes. Rules currently enforced include: a static command or query
cannot be empty, a dynamic one needs an allow list, agent authentication takes no
credentials, a secret has one source and not two, a read only action may only permit
`select`, a GET carries no body, input keys are unique and well formed, a password
input carries no default, and Anthropic models reject `temperature`.

## Status codes for unmatched URLs

The security chain runs before the DispatcherServlet, so by default every unknown path
answers `401`: a typo in a URL is indistinguishable from a missing token.
`RestAuthenticationEntryPoint` asks the handler mapping whether the request would have
reached a controller and answers accordingly.

| Request | Status |
|---|---|
| Path maps to nothing | `404 NOT_FOUND` |
| Path exists, verb does not | `405 METHOD_NOT_ALLOWED` |
| Path exists and is protected, no token | `401 UNAUTHENTICATED` |
| Authenticated but role insufficient | `403 ACCESS_DENIED` |
| Path variable cannot be converted | `400 INVALID_PARAMETER` |

The same answers apply with and without a token, so a client never has to guess whether
`401` meant "wrong URL" or "log in again".

> Trade-off: an anonymous caller can now discover which paths exist. For an internal
> control plane whose API surface is documented anyway, correct status codes are worth
> more than that much obscurity. `HttpStatusContractTest` pins the behaviour, so
> reverting is a deliberate act rather than an accident.

## Error shape

Every failure, including those raised inside the security filter chain, is returned as:

```json
{
  "status": 422,
  "error": "BUSINESS_RULE_VIOLATION",
  "message": "Request violates domain rules",
  "path": "/api/v1/definitions",
  "details": [
    { "field": "actions[0].config.command", "message": "A static command cannot be empty" }
  ],
  "timestamp": "2026-08-21T09:41:00Z"
}
```

## Tests

```bash
./mvnw test                              # unit and wiring tests, no infrastructure
RUN_INTEGRATION_TESTS=true ./mvnw test   # adds the full context test, needs Postgres and Redis
```

`ApplicationWiringTest` builds the entire object graph without a database: it proves
component scanning, constructor injection, the security filter chain and the aspect
proxying all work. `PropertyBindingTest` asserts the settings classes bind
correctly from YAML, using `src/test/resources/application.yml` — the suite switches the
config client off, so it never depends on a config server being up. Whether the *served*
configuration is complete is asserted in mcp-config, against the file it serves.
`ActionRuleValidatorTest` covers the domain rules directly.
