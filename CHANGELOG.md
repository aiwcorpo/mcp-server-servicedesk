# Changelog

Format: wersje SemVer, od najnowszej. Do pierwszego wydania publicznego obowiązuje `0.1.0-SNAPSHOT`.

## Niewydane

- Starter 3.0.0-beta-14, kontrakty 6.3.2 (bez zmian w kodzie serwera; hashe definicji narzędzi jak wcześniej).
- Apache-2.0: `LICENSE`, `NOTICE` (także w `META-INF` każdego jara), licencja i `scm` w `pom.xml`.
- `SECURITY.md`, `CONTRIBUTING.md`, workflow `release.yml` (obraz ghcr.io, manifest i SBOM CycloneDX w wydaniu GitHub).
- CI: akcje GitHub przypięte do SHA, obrazy CI po digeście, uprawnienia minimalne per job.
- README: instalacja, codzienna obsługa, bez ścieżek z katalogu użytkownika; `ci/run.sh` sam szuka JDK 25.
