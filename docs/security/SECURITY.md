# Bezpieczeństwo serwera

Niezmienniki platformy (`mcp-platform-plan/00-overview.md`) i to, gdzie serwer je spełnia.

| Niezmiennik | W serwerze |
| --- | --- |
| Ruch tylko od gateway'a: mTLS + wewnętrzny JWT, inaczej 401 | `server.ssl.client-auth: need` z CA wewnętrznym; `InternalJwtFilter` startera (CN `mcp-gateway`, ES256 z JWKS gateway'a, `iss`, `aud`, czas życia <= 60 s, `jti` jednorazowy w Redisie, `tool` == `params.name`, `rid` == `X-Request-Id`). Bez JWT tylko `/actuator/health*`. Test: `GatewayOnlyAccessTest` |
| Token IdP nigdy do systemu | serwer go nie dostaje; do systemu idzie wyłącznie poświadczenie z C3 (`CredentialContext.require`) |
| Fail-closed | brak JWKS, Redisa, konfiguracji albo poświadczenia -> 401/503/`credential_required`/brak startu, nigdy przepuszczenie; starter odrzuca relaksacje `dev` w innych profilach |
| Sekrety nigdy w logach, audycie, wynikach i błędach | nagłówki poświadczeń ukryte po odczycie, `BackendCredential.toString()` bez wartości, redakcja komunikatów w starterze; adapter nie czyta treści błędów systemu. Test: `theCredentialAndTheBackendErrorNeverAppearInTheResultOrTheLog` |
| "Nie istnieje" i "brak dostępu" -> `not_found_or_denied` | 404 i 403 systemu, zgłoszenie lub kolejka spoza dozwolonych, alias i zgłoszenie bez kolejki dają ten sam błąd i tekst (porównanie bajtowe). Testy: `QueueBoundaryTest`, `missingAndForbiddenTicketsLookTheSame` |
| Konto serwisowe: serwer jest granicą | tryb `service-account` obsługuje tylko kolejki z `MCP_SERVICEDESK_ALLOWED_QUEUES`, pusta lista = odmowa wszystkiego bez wołania systemu; poświadczenie oznaczone `service-account` jest trzymane listą w każdym trybie. Testy: `QueueBoundaryTest`, `NoAllowedQueuesTest`, `UserSecretModeTest` |
| Audyt z `requestId` | jedno zdarzenie C8 `tool_call` na wywołanie (logger `pl.aiwcorpo.mcp.audit`, stdout, czysta linia JSON), `requestId` z JWT |

## Zasady dla kodu

- Każdy argument narzędzia to dane od modelu: wzorzec, długość, zamknięte listy, przed wywołaniem systemu.
  Szczegóły: `docs/jak-napisac-narzedzie.md`.
- Adresy: tylko `MCP_<SYSTEM>_URL` z konfiguracji, ścieżki API z szablonu URI z argumentami jako zmiennymi
  (kodowane ściśle przez `UriComponentsBuilder.encode()`), bez przekierowań; żadnych adresów z argumentów (SSRF).
- Serwer nie trzyma poświadczeń. Konto serwisowe service desku (`service-account`, D10) przychodzi przez C3 jak
  każde inne; wtedy serwer jest główną granicą autoryzacji: `QueueAccessPolicy` ocenia kolejkę zwróconą przez
  system przy każdym wywołaniu (README, "Granica kolejek"). Polityka zasobów Cedar ze startera beta dojdzie w
  tym samym miejscu (M3).
- Ścieżki API pochodzą z konfiguracji walidowanej przy starcie; argumenty idą tylko jako zmienne URI kodowane
  ściśle albo wartości JSON. Treść notatki dostaje stałe pola z konfiguracji (np. widoczność wewnętrzna).
- Hinty MCP (`readOnlyHint`, `destructiveHint`) są informacją dla klienta, nie zabezpieczeniem.

## Wdrożenie

Konto bez uprawnień, sandbox systemd (`deploy/systemd/mcp-servicedesk.service`), sekrety tylko w pliku
środowiska (0640, root:mcp) albo z magazynu sekretów, certyfikat serwera z SAN `mcp-servicedesk.internal` z CA
wewnętrznego. Redis: użytkownik ACL `mcp-servicedesk` z kluczami `jti:mcp-servicedesk:*` i komendą `SET`.
