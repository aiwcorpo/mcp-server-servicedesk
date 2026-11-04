# mcp-servicedesk

MCP server for a service desk, one of the native servers of the aiwcorpo MCP platform. It lets an MCP client search the
tickets of a queue, read a ticket and add a note. It is a deployable product that runs next to the platform
gateway, not a library.

It is built on `mcp-platform-starter` 3.0.0-beta-14 (Java 25, Spring Boot 4.1.0, Spring AI 2.0.0, `mcp-contracts` 6.3.2) and
runs only behind the gateway. It accepts traffic over mTLS with the gateway's internal JWT and answers 401 without it; there
is no anonymous mode. Logging users in, deciding who may call a tool and confirming actions are the gateway's job.

Platform documentation (gateway, policies, portal, operation) lives in
[aiwcorpo/mcp-docs](https://github.com/aiwcorpo/mcp-docs). This README covers only what belongs to this server.

## Which service desk

No service desk product is chosen yet. The adapter is a generic REST client: paths, query parameters, the note body and the
position of every field in the JSON come from configuration (`servicedesk.api`, see "REST adapter" below). Without a
configuration file the server uses a reference layout, which is what the tests run against. To adopt a product, write its `servicedesk.api` file
and check the first calls against a real instance; the tests use a stub (`StubBackend`).

The only accepted credential type is `service-account`: a key of a technical account that the credential broker hands to the
gateway. The service desk does not know the individual user, so access to queues comes only from the policy bundle: the groups
`mcp-servicedesk-queue-<id>` grant a queue, and no configuration of the server opens one. A call with any other credential type
fails with `internal_error` before the policy and the backend are touched.

## Tools

| Tool | Risk | Action | What it does |
| --- | --- | --- | --- |
| `search_tickets` | `READ_ONLY` | `read` | Searches the tickets of one queue for a text (up to 20 results) |
| `get_ticket` | `READ_ONLY` | `read` | Returns one ticket: id, queue, status, priority, dates, subject, description |
| `add_note` | `UPDATE` | `update` | Adds a note to a ticket |

The client sees them as `servicedesk__<tool>`. Risk, action and the group a call requires are in the generated
[tool reference](https://github.com/aiwcorpo/mcp-docs/blob/main/docs/reference/tools.md)
(`docs/reference/tools.md` in `mcp-docs`); arguments and results are in `docs/api/tools-catalog.md`. `add_note` waits in the
gateway for an administrator's approval after every change of its definition.

Behaviour worth knowing before you deploy:

- A queue is judged on what the service desk reports at call time, not on what the model passed. A ticket moved to a queue
  the caller has no grant for stops being available on the next call. A ticket the caller may not see, a missing ticket and a
  failed read all give the same `not_found_or_denied`, because the service account cannot tell the caller which of them it was.
  The cause is written to the log (`resolver_failed_hidden`) and to the audit.
- A queue without a classification is treated as unrestricted. This is deliberate. A classification that cannot be read
  (an object, a number, hidden characters, more than 64 characters) is an error.
- Subject and description are written by people, so they reach the model as `UntrustedText`: HTML converted to text,
  control and invisible characters replaced, cut to 500 and 8000 characters.
- Every note starts with the line `[Added through the MCP platform on behalf of <user>]`, because the service desk sees only
  the service account as the author. The name comes from the gateway's token, not from tool arguments. The note text from the
  model is escaped, and a text that contains the phrase `Added through the MCP platform` is rejected as `invalid_argument` so
  that nobody can forge a second attribution.
- `add_note` is delivered at least once. After a `502` from a balancer the client may repeat the call and the service desk then
  holds two notes: generic REST has no idempotency key or conditional write.
- Between reading a ticket and writing the note the ticket may change queue. The server reads it again right before writing and
  checks grants and policy, but generic REST offers no conditional write to close the window completely.

## REST adapter

`servicedesk.api` describes the API of the product. It is validated at startup (paths contain only letters, digits and
`/_.-` plus the required placeholders; no `..`, `?` or `#`), and an invalid configuration stops the start. Override the
reference layout with a file passed in `--spring.config.additional-location`.

| Key | Meaning | Reference value |
| --- | --- | --- |
| `credential-header` | Header carrying the credential; `Authorization` gets `<scheme> <value>`, any other header only the value | `Authorization` |
| `ticket-path` | Ticket, placeholder `{ticketId}` | `/api/v1/tickets/{ticketId}` |
| `queue.path`, `queue.id-pointer` | Queue (existence and classification), placeholder `{queue}`; id of the queue in the response | `/api/v1/queues/{queue}`, `/id` |
| `queue.classification-pointer` | Classification of the queue (string or array of strings); empty or missing means unrestricted | `/classification` |
| `search.path`, `search.queue-param` | Search; the queue goes into the path as `{queue}` or into a parameter | `/api/v1/queues/{queue}/tickets` |
| `search.text-param`, `search.limit-param` | Parameters for the text (a value, not a query language) and the limit (the server asks for 21) | `q`, `limit` |
| `search.results-pointer` | Array of hits in the response; empty means the response is the array | `/items` |
| `note.path`, `note.method` | Where and how a note is written (`POST`, `PUT` or `PATCH`) | `/api/v1/tickets/{ticketId}/notes`, `POST` |
| `note.body-pointer`, `note.id-pointer` | JSON positions of the note text and of the returned note id | `/body`, `/id` |
| `note.static-fields` | Fixed fields of every note (`pointer` and a JSON literal), such as internal visibility so that the note never reaches the ticket's requester | `/visibility` = `"internal"` |
| `fields.*` | JSON pointers of `id`, `queue` (required), `subject`, `status`, `priority`, `created`, `updated`, `description` | `/id`, `/queue`, ... |

```yaml
servicedesk:
  api:
    credential-header: X-API-Key
    ticket-path: /rest/requests/{ticketId}
    queue: {path: /rest/queues/{queue}, id-pointer: /queueId, classification-pointer: /category}
    search: {path: /rest/requests, queue-param: queueId, text-param: searchTerm, limit-param: limit, results-pointer: /values}
    note:
      path: /rest/requests/{ticketId}/comments
      body-pointer: /body
      static-fields: [{pointer: /public, value: 'false'}]
      id-pointer: /id
    fields: {id: /issueId, queue: /serviceDesk/queueId, subject: /fields/summary, status: /currentStatus/status,
             created: /createdDate/iso8601, description: /fields/description}
```

Search text and ids go into the request only as strictly encoded URI variables, and the note text as a JSON value. Only the
configured pointers are read from a response, and redirects are not followed. A response is limited to 1 MB.

## Build and test

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"   # macOS; on Linux the JDK 25 directory
./mvnw verify        # jar: mcp-app/target/mcp-servicedesk.jar and mcp-app/target/mcp-manifest.json
make ci              # ci/run.sh verify: text checks, clean verify, required test counts, manifest present
```

Tests need no Docker and no platform environment. `TestGateway` from `mcp-platform-test` signs internal JWTs, and `StubBackend`
is an HTTP server that imitates a service desk in the reference layout.

Platform artifacts (`mcp-platform-starter`, `mcp-contracts`) come from the GitHub Packages registry of `aiwcorpo` or from
`~/.m2`; the setup is described in the README of `mcp-starter-lib`. Linux and macOS with bash 3.2 or newer; Windows only
through WSL2.

The build writes `mcp-app/target/mcp-manifest.json`. That file, not the answer of a running server, is what CI registers with
the gateway.

Modules: `mcp-core` (domain and ports), `mcp-application` (use cases and the queue check), `mcp-integration` (REST adapter,
resource resolvers), `mcp-tools` (MCP tools), `mcp-app` (bootstrap, the single jar, end-to-end tests).

## Configuration

`application.yml` is the production configuration: mTLS, JWKS of the gateway over TLS, `jti` store in Redis, `https` to the
service desk. The server does not start without these:

| Variable | Meaning |
| --- | --- |
| `MCP_TLS_KEYSTORE`, `MCP_TLS_TRUSTSTORE`, `MCP_TLS_STORE_PASSWORD` | PKCS12 files: server certificate (SAN `mcp-servicedesk.internal`) and the internal CA |
| `MCP_GATEWAY_JWKS_URI` | JWKS of the gateway's internal JWT (`https`) |
| `MCP_CONNECT_URL` | Portal page for connecting an account, with `{system}`; sent in `credential_required` |
| `MCP_SERVICEDESK_URL` | Base URL of the service desk API, without query or fragment (`https` outside the `dev` profile) |
| `MCP_POLICY_URL`, `MCP_POLICY_KEY_ID`, `MCP_POLICY_PUBLIC_KEY_FILE`, `MCP_POLICY_STATE_DIR` | Signed policy bundle (Ed25519) and the directory that keeps the last accepted revision; without a bundle within 30 s the server does not start |
| `MCP_INTERNAL_URL` | Address of this server in the manifest |
| `MCP_REDIS_HOST`, `MCP_REDIS_PORT`, `MCP_REDIS_USER`, `MCP_REDIS_PASSWORD` | Redis for single-use `jti` (ACL user `mcp-servicedesk`) |

Optional: `MCP_SERVICEDESK_SSL_BUNDLE` (SSL bundle for the service desk, such as an internal CA), `MCP_GATEWAY_ISSUER`,
`MCP_PORT` (`8443`) and `MCP_BIND`. `MCP_SERVICEDESK_ALLOW_PLAIN_HTTP=true` allows `http` outside `dev` and exists only for the
mocks of `mcp-platform-dev`: the credential then travels in clear text. Defaults and property names are in the
[server environment reference](https://github.com/aiwcorpo/mcp-docs/blob/main/docs/reference/env-servers.md)
(`docs/reference/env-servers.md` in `mcp-docs`). The MCP endpoint is `/mcp`; without a JWT only `/actuator/health` answers.

There are no variables for allowed queues or for the credential mode. Queues come from the policy bundle only.

## Install

The server is one jar or one image. For a VM with systemd use `deploy/scripts/install.sh mcp-app/target/mcp-servicedesk.jar` and the
environment template `deploy/systemd/mcp-servicedesk.env.template`; `docs/operations/deployment-vm-systemd.md` has the steps. For a
container run `docker build -t mcp-servicedesk:<tag> .` after `./mvnw verify` (image `eclipse-temurin:25-jre` on glibc, because
`cedar-java` loads a native library; user `mcp`, port 8443). After an update register the fresh manifest with the gateway.
Operating notes, upgrades and troubleshooting are in `mcp-docs`.

## Run locally without the platform

The `dev` profile must be the only active profile. It serves plain HTTP on 127.0.0.1, keeps `jti` in memory and needs no
certificates. There is no gateway, so it only shows that the server starts and rejects requests without a JWT:

```bash
SPRING_PROFILES_ACTIVE=dev MCP_PORT=18530 "$JAVA_HOME/bin/java" -jar mcp-app/target/mcp-servicedesk.jar
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:18530/actuator/health    # 200
curl -s -o /dev/null -w '%{http_code}\n' -X POST -H 'Content-Type: application/json' \
     -H 'Accept: application/json, text/event-stream' \
     -d '{"jsonrpc":"2.0","id":1,"method":"ping"}' http://127.0.0.1:18530/mcp       # 401
```

The `dev` profile accepts JWTs signed with the published test keys of the contracts and needs no client certificate. For that
reason it listens on loopback only, and the starter refuses to start `dev` on another address. Never enable it on a host or in a
container that the gateway connects to, and never put `SPRING_PROFILES_ACTIVE=dev` into an `.env` file, a systemd unit or an
image. To run against the `mcp-platform-dev` environment with mTLS, see `mcp-docs`.

## Documentation in this repository

- `docs/jak-napisac-narzedzie.md`: how to add a tool.
- `docs/architecture/overview.md`: architecture and decisions.
- `docs/security/SECURITY.md`: security invariants of this server.
- `docs/api/tools-catalog.md`: tool registry.
- `docs/operations/deployment-vm-systemd.md`, `deploy/`, `Dockerfile`: deployment.

These pages are still in Polish. Vulnerability reports: `SECURITY.md`. Contributions: `CONTRIBUTING.md`. Changes: `CHANGELOG.md`.

## License

Apache-2.0, see `LICENSE`.
