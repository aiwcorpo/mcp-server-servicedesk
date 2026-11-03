# Security policy

## Reporting a vulnerability

Report vulnerabilities privately through GitHub: Security tab of this repository, "Report a vulnerability"
(private vulnerability reporting). Do not open a public issue for a security problem.

Include the affected version (`pom.xml`), the tool involved, the shape of the `servicedesk.api` configuration (paths and
pointers, without credentials) and steps to reproduce. In scope: a tool result that contains a ticket the caller's queue
grants do not cover, a ticket moved to a queue the caller has no grant for that is still served or written to, a note that
lands outside the judged ticket, a way to make the server send a model-written query or path to the service desk, a way
to forge the attribution line of a note, and any path by which the service-account key reaches a log, a result or an error.

## Scope

In scope: the code of this repository. Out of scope: the platform library (`mcp-starter-lib`), the gateway, the credential
broker and the policies, which are separate repositories with their own policies; the service desk product itself.

The invariants of the server and the tests that hold them are in `docs/security/SECURITY.md`.

## Supported versions

Only the latest released version receives fixes. Published versions are immutable; a fix ships as a new release.
