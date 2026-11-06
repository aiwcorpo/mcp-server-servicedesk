# Changelog

Versions follow SemVer, newest first.

## 2.0.0

Major release: the policies of the server live in this repository. Built on `mcp-platform-starter` 4.0.0 and `mcp-contracts` 7.0.0.

- Cedar policies in `mcp-app/src/main/resources/mcp/policies/` (schema in the namespace `ServiceDesk`, `resource-types.json`, `read.cedar`,
  `update.cedar`), carried in the manifest with `policiesHash`. They replace `policies/servicedesk/` of the `mcp-policies` repository; the
  rules are the same, except what the platform now generates for every server: the call rules by group, the early check of the
  queue named by a write tool, the forbid for `restricted` queues (from `ServiceDesk::Queue.classification`, for the queue and its
  tickets) and the rule for the service account outside the queues of the user.
- A deployed server whose policies or declarations changed waits for approval in the gateway: it refuses calls (`policies_not_approved`,
  `policies_differ`) until an administrator approves the new `policiesHash`. Register the new manifest and approve before the first call.
- The queue and the ticket describe the scope `ServiceDesk::Queue/<id>` of the queue the service desk reports. The queue resolver has
  the probe, the ticket resolver the probe for a caller with no queue scope (`scopeTypes`, `probeOutsideScopes`) and hides a failure
  of the service desk like a missing ticket. The id grammar is the `idPattern` of the declared types: the `ServiceDesk*` definitions
  no longer exist in the contracts, and the tool arguments take their pattern from the declarations (`@ArgumentLimits(resourceType = ...)`).
- Removed: `MCP_POLICY_URL`, `MCP_POLICY_KEY_ID`, `MCP_POLICY_PUBLIC_KEY_FILE`, `MCP_POLICY_STATE_DIR`, the signed policy bundle and the
  `StateDirectory` of the unit. Added: `MCP_GATEWAY_POLICY_DATA_URL` (and `MCP_GATEWAY_TLS_NAME` when the host in it is not a name of the
  gateway certificate). The server connects to the gateway for the policy data with its own certificate, which now needs the usage
  `clientAuth` next to `serverAuth` (CN `mcp-servicedesk`, SAN `mcp-servicedesk.internal`).
- `deploy/` has `compose.yaml`, `mcp-servicedesk.env` and `certs/` next to the systemd files, and the procedure that adds the system
  `servicedesk` in the broker and the server `mcp-servicedesk` in the gateway without restarting anything.
- `.run/` has IntelliJ configurations for the server and for the `LocalGateway` of `mcp-platform-test`, with the policies directory and a scope mapping.

## 1.0.0 - 2026-11-04

First public release. Built on `mcp-platform-starter` 3.0.0 (contracts 6.3.2).

- MCP server for a service desk with three tools: `search_tickets` (tickets of one queue, up to 20 results), `get_ticket` and
  `add_note`. The only accepted credential type is `service-account`; access to queues comes from the gateway policy
  (`mcp-servicedesk-queue-<id>` groups) and an empty queue list in the configuration means refusal.
- Generic REST adapter: paths, query parameters, the note body and the position of every field in the JSON come from the
  `servicedesk.api` configuration, so a product can be adopted without code changes. The tests run against a stub backend.
- Tool titles are English: "Search tickets", "Get ticket", "Add note".
- Apache-2.0 licence, NOTICE (also in `META-INF` of every jar), SECURITY.md, CONTRIBUTING.md.
- CI through one script, `ci/run.sh`, for GitLab, Jenkins and GitHub Actions; the `release.yml` workflow publishes the image to
  ghcr.io and attaches the manifest and a CycloneDX SBOM to the GitHub release.
