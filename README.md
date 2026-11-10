# mcp-servicedesk

MCP server for a service desk. AI clients such as Claude Code reach it through the gateway of the aiwcorpo MCP platform to search
the tickets of a queue, read a ticket and add a note.

No service desk product is chosen yet. The server is a generic REST client: paths, query parameters and the position of every field
in the JSON come from the configuration `servicedesk.api`, described below. Without a configuration file it uses a reference
layout, which the tests run against.

## Quick start

```bash
cd deploy
# fill in MCP_GATEWAY_JWKS_URI, MCP_CONNECT_URL and MCP_SERVICEDESK_URL in mcp-servicedesk.env
docker compose up -d
docker compose logs mcp-servicedesk | grep event=tls_certificate
```

Then the platform operator adds `https://<this host>:8443/mcp` in the portal. Full steps: [deploy/README.md](deploy/README.md).
To fit your product, write a `servicedesk.api` file first (next section).

## Tools

3 tools, shown to a client as `servicedesk__<name>`. Reading needs the group `mcp-servicedesk-read`, writing `mcp-servicedesk-write`.
A user also needs a group mapped to a queue (`ServiceDesk::Queue/<id>`); no setting of the server opens a queue.

| Tool | Access | What it does |
| --- | --- | --- |
| `search_tickets` | read | Searches the tickets of one queue for a text, up to 20 results |
| `get_ticket` | read | Returns one ticket: id, queue, status, priority, dates, subject, description |
| `add_note` | write | Adds a note to a ticket |

The service desk is called with one service account of the platform (the only accepted credential type, `service-account`), so
it sees the author of a note as that account. Every note therefore starts with the line
`[Added through the MCP platform on behalf of <user>]`, and a text that contains the phrase `Added through the MCP platform` is
rejected. `add_note` is delivered at least once: after a `502` from a balancer a repeated call can leave two notes.

A ticket the caller may not see, a missing ticket and a failed read all answer `not_found_or_denied`. A queue is judged on what the
service desk reports at call time, so a ticket moved to another queue follows the grants of the new one. Queues that the service
desk reports as `restricted` are closed to everyone.

## Configure the REST adapter

`servicedesk.api` is checked at startup; an invalid file stops the server. Paths may contain only letters, digits and `/_.-` plus
the placeholders below, no `..`, `?` or `#`. Values in the last column are the reference layout.

| Key | Meaning | Reference value |
| --- | --- | --- |
| `credential-header` | header with the credential; `Authorization` gets `<scheme> <value>`, any other header only the value | `Authorization` |
| `ticket-path` | one ticket, placeholder `{ticketId}` | `/api/v1/tickets/{ticketId}` |
| `queue.path` | one queue (existence, classification), placeholder `{queue}` | `/api/v1/queues/{queue}` |
| `queue.id-pointer` | JSON pointer to the queue id in that response | `/id` |
| `queue.classification-pointer` | JSON pointer to the classification (string or array of strings); empty or missing means unrestricted | `/classification` |
| `search.path` | search; the queue is `{queue}` in the path or a parameter | `/api/v1/queues/{queue}/tickets` |
| `search.queue-param` | parameter that carries the queue, when it is not in the path | none |
| `search.text-param`, `search.limit-param` | parameters for the text (a plain value, no query language) and the limit (the server asks for 21) | `q`, `limit` |
| `search.results-pointer` | array of hits in the response; empty means the response is the array | `/items` |
| `note.path`, `note.method` | where and how a note is written: `POST`, `PUT` or `PATCH` | `/api/v1/tickets/{ticketId}/notes`, `POST` |
| `note.body-pointer`, `note.id-pointer` | JSON positions of the note text and of the returned note id | `/body`, `/id` |
| `note.static-fields` | fixed fields of every note: `pointer` and a JSON literal, for example internal visibility | `/visibility` = `"internal"` |
| `fields.id`, `fields.queue` | pointers of the ticket id and queue (required) | `/id`, `/queue` |
| `fields.subject`, `status`, `priority`, `created`, `updated`, `description` | pointers of the other ticket fields | `/subject`, `/status`, `/priority`, `/createdAt`, `/updatedAt`, `/description` |

Example for a product with its own layout:

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

Pass the file to the server with `SPRING_CONFIG_ADDITIONAL_LOCATION=file:/path/to/api.yml` in the environment file. For Compose,
mount the file into the container as well ([deploy/README.md](deploy/README.md)). Check the first calls against a real instance:
the tests use a stub, not a product. A response is limited to 1 MB, only the configured pointers are read, and redirects are
not followed.

## Configuration

Required, in `deploy/mcp-servicedesk.env`:

| Variable | Description |
| --- | --- |
| `MCP_GATEWAY_JWKS_URI` | key set of the gateway: `https://<platform>/.well-known/mcp-jwks.json` |
| `MCP_CONNECT_URL` | portal page where a user connects an account: `https://<platform>/connections?system={system}` |
| `MCP_SERVICEDESK_URL` | base address of the service desk API, `https`, without query or fragment |

Optional settings (`MCP_SERVICEDESK_SSL_BUNDLE` for an internal CA, own certificate, Redis for several instances) are commented
in the same file.

## Build

```bash
export JAVA_HOME=<path to JDK 25>
./mvnw verify
```

The jar is `mcp-app/target/mcp-servicedesk.jar`; `docker build -t mcp-servicedesk:dev .` builds the image. Tests need neither Docker
nor a service desk. Working on the code: [CONTRIBUTING.md](CONTRIBUTING.md).

## More

- [deploy/README.md](deploy/README.md): installation, troubleshooting, upgrade from 1.x
- [CHANGELOG.md](CHANGELOG.md), [SECURITY.md](SECURITY.md)

Apache License 2.0.
