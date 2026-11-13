# Contributing

The server follows the contracts of `mcp-contracts` and the library `mcp-starter-lib`. A change that needs a different
contract (a resource type, a query field, a credential type) starts as a `proposed` entry in `CONTRACT_CHANGES.md` of
`mcp-contracts` and waits for approval.

## Setup

JDK 25, Linux or macOS with bash 3.2 or newer; Windows only through WSL2. The platform artifacts come from Maven Central or must be
in `~/.m2` (see the README of `mcp-starter-lib`). No container runtime and no service desk are
needed: the tests run the whole server against a stub backend.

```bash
./mvnw -B verify     # the same command runs in CI
```

`./mvnw -B verify` must pass before a merge. A new tool needs the items of the [tool guide](https://github.com/aiwcorpo/mcp-docs/blob/main/docs/developer/tools.md) in `mcp-docs`: risk, title,
`@ResourceRef` for every id it names, a closed argument schema, a negative test, and a row in the tools table of the README.

## Policies

The Cedar policies of the service desk are in `mcp-app/src/main/resources/mcp/policies/` and go into the jar and the manifest:

| File | Content |
| --- | --- |
| `schema.cedarschema` | namespace `ServiceDesk`: `ServiceDesk::Queue` (optional classification) and `ServiceDesk::Ticket` (parent: its queue), actions `read`, `update`, `delete`, `admin` |
| `resource-types.json` | the types: `idPattern`, `scopePattern`, `earlyScopeCheck` for queues, the classification, the parent |
| `read.cedar` | reading needs `mcp-servicedesk-read` |
| `update.cedar` | changing a queue or a ticket needs `mcp-servicedesk-write` and the scope of the queue; deleting a ticket needs `mcp-servicedesk-destructive` and the scope; a queue is never deleted |

The platform generates the rest: the group a tool needs, the early check of a queue named by a write tool, the closed `restricted`
queues and the rule for the service account. That rule matters here: the service account sees every queue, so a request outside a
queue scope of the user is refused, for reads and for changes. The resolvers give a queue and each of its tickets the scope
`ServiceDesk::Queue/<id>` of the queue the service desk reports now. A caller with no queue scope is refused before the service desk
is asked (`scopeTypes` and `probeOutsideScopes` of the ticket resolver, `probe` of the queue resolver), and a failing service desk
looks like a missing ticket (`hidesFailureForServiceAccount`). The tools limit queries to the granted queues with `QueryScope` and
judge every search hit again. `ServicedeskPolicyDecisionsTest` states the decisions. A change to any file here changes
`policiesHash` in the manifest and needs a new approval.

The gateway pushes the scope mapping, the allowed AI clients and the emergency overrides to `POST /.well-known/mcp-policy-data`
after every change and every minute. A server without fresh data answers `tools/call` with `409 policy_data_stale`, and the gateway
pushes and repeats the call.

## Behaviour of the tools

- A queue without a classification is treated as unrestricted, on purpose. A classification that cannot be read (an object, a
  number, hidden characters, more than 64 characters) is an error.
- Subject and description reach the model as `UntrustedText`: HTML converted to text, control and invisible characters replaced, cut
  to 500 and 8000 characters.
- The note text is escaped, and the author name in the first line comes from the gateway's token, not from tool arguments.
- Between reading a ticket and writing the note the ticket may change queue. The server reads it again right before writing and
  checks grants and policy; generic REST has no conditional write to close the window completely.
- Search text and ids go into the request only as strictly encoded URI variables, and the note text as a JSON value.

## Tests and build

`TestGateway` from `mcp-platform-test` signs internal JWTs, and `StubBackend` is an HTTP server that imitates a service desk in the
reference layout. The GitHub, GitLab and Jenkins files run `./mvnw -B verify`.

The build writes `mcp-app/target/mcp-manifest.json`, the document that the running server serves at `/.well-known/mcp-manifest`.
It is kept as an artifact so that a reviewer can read what a commit publishes.

Modules: `mcp-core` (domain and ports), `mcp-application` (use cases and the queue check), `mcp-integration` (REST adapter,
resource resolvers), `mcp-tools` (MCP tools), `mcp-app` (bootstrap, the single jar, end-to-end tests).

## Running without the platform

The `dev` profile serves plain HTTP on 127.0.0.1 and accepts JWTs signed with the published test keys of the contracts. It only
shows that the server starts and rejects requests without a JWT:

```bash
SPRING_PROFILES_ACTIVE=dev MCP_PORT=18530 "$JAVA_HOME/bin/java" -jar mcp-app/target/mcp-servicedesk.jar
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:18530/actuator/health    # 200
```

For a run on the policies of this working tree, `.run/` has IntelliJ configurations: `LocalGateway` (serves the JWKS on port 18445,
uses the service account as credential type and pushes policy data with the mapping in `.run/scope-mapping.json`) and
`McpServicedesk (dev, behind LocalGateway)`. Restart `LocalGateway` after a change to the policies.

Never enable `dev` on a host that the gateway connects to, and never put it in an `.env` file, a systemd unit or an image.

## Release

A pushed tag `v<version>` runs `.github/workflows/release.yml`, which pushes the image to
`ghcr.io/aiwcorpo/mcp-server-servicedesk` and creates the GitHub Release from the tagged commit. The workflow cannot check who
pushed the tag or whether the commit was reviewed; set this up in the repository settings before the first tag:

1. Settings > Environments: create `release` with required reviewers, "Prevent self-review" on, and deployment branches and tags
   limited to tags `v*`.
2. Settings > Rules > Rulesets: a tag ruleset for `v*` that restricts creation, update and deletion to the release maintainers.
3. Package settings of `mcp-server-servicedesk` on ghcr.io: write access only for this repository and the release maintainers.

## Commits

Subject in English, imperative, up to 72 characters. The body describes the change, not the process. Tests go in the
same commit as the code they cover.

## License

Contributions are licensed under Apache-2.0 (see `LICENSE`).
