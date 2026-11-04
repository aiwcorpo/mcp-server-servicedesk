# Changelog

Versions follow SemVer, newest first. Until the first public release the working version is `0.1.0-SNAPSHOT`.

## Unreleased

- Starter 3.0.0-beta-14, contracts 6.3.2 (no change in the server code; tool definition hashes are unchanged).
- Apache-2.0: `LICENSE`, `NOTICE` (also in `META-INF` of every jar), licence and `scm` in `pom.xml`.
- `SECURITY.md`, `CONTRIBUTING.md`, the `release.yml` workflow (ghcr.io image, manifest and CycloneDX SBOM in the GitHub release).
- CI: GitHub actions pinned to SHAs, CI images pinned by digest, minimal permissions per job.
- README: installation and daily operation, no paths from a user's home directory; `ci/run.sh` finds JDK 25 on its own.
