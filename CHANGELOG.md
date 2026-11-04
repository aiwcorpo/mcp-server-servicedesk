# Changelog

Versions follow SemVer, newest first.

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
