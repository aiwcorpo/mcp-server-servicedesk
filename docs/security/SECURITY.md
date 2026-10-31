# Bezpieczeństwo serwera

Niezmienniki platformy (`mcp-platform-plan/00-overview.md`) i to, gdzie serwer je spełnia.

| Niezmiennik | W serwerze |
| --- | --- |
| Ruch tylko od gateway'a: mTLS + wewnętrzny JWT, inaczej 401 | `server.ssl.client-auth: need` z CA wewnętrznym; `InternalJwtFilter` startera (CN `mcp-gateway`, ES256 z JWKS gateway'a, `iss`, `aud`, czas życia <= 60 s, `jti` jednorazowy w Redisie, `tool` == `params.name`, `rid` == `X-Request-Id`). Bez JWT tylko `/actuator/health*`. Test: `GatewayOnlyAccessTest` |
| Token IdP nigdy do systemu | serwer go nie dostaje; do systemu idzie wyłącznie poświadczenie z C3 (`CredentialContext.require`) |
| Fail-closed | brak JWKS, Redisa, konfiguracji albo poświadczenia -> 401/503/`credential_required`/brak startu, nigdy przepuszczenie; starter odrzuca relaksacje `dev` w innych profilach |
| Sekrety nigdy w logach, audycie, wynikach i błędach | nagłówki poświadczeń ukryte po odczycie, `BackendCredential.toString()` bez wartości, redakcja komunikatów w starterze; adapter zamienia błędy i nieczytelne odpowiedzi systemu na wyjątki bez treści; system tylko po `https`. Testy: `theCredentialAndTheBackendErrorNeverAppearInTheResultOrTheLog`, `anUnreadableBodyStaysOutOfTheResultAndTheLog`, `outsideDevTheBackendMustBeHttps` |
| "Nie istnieje" i "brak dostępu" -> `not_found_or_denied` | 404 i 403 systemu, zgłoszenie lub kolejka spoza przyznanych, alias i zgłoszenie bez kolejki dają ten sam błąd i tekst (porównanie bajtowe). Testy: `QueueBoundaryTest`, `missingAndForbiddenTicketsLookTheSame` |
| Konto serwisowe: kolejki tylko z paczki polityk | jedyny typ poświadczenia to `service-account`; kolejkę otwiera wyłącznie grupa `mcp-servicedesk-queue-<id>` w paczce (CC-163), konfiguracja serwera nie otwiera żadnej; `ServiceDesk::Ticket` bez zakresu kolejki i kolejka spoza grantów dają `not_found_or_denied` przed resolverem i systemem. Testy: `QueueBoundaryTest`, `ResourcePolicyTest`, `ProductionConfigurationTest` |
| Audyt z `requestId` | jedno zdarzenie C8 `tool_call` na wywołanie (logger `pl.aiwcorpo.mcp.audit`, stdout, czysta linia JSON), `requestId` z JWT |

## Zasady dla kodu

- Każdy argument narzędzia to dane od modelu: wzorzec, długość, zamknięte listy, przed wywołaniem systemu.
  Szczegóły: `docs/jak-napisac-narzedzie.md`.
- Adresy: tylko `MCP_SERVICEDESK_URL` z konfiguracji (poza `dev` wyłącznie `https`; `http` tylko jawnym
  `MCP_SERVICEDESK_ALLOW_PLAIN_HTTP` dla mocków środowiska dev), ścieżki API z szablonu URI z argumentami jako
  zmiennymi (kodowane ściśle przez `UriComponentsBuilder.encode()`), bez przekierowań; żadnych adresów z
  argumentów (SSRF).
- Odpowiedź systemu: najwyżej `max-response-size` (1 MB), tylko pola z mapowania, nigdy w komunikacie ani logu
  (także odpowiedź 2xx, której nie da się sparsować). Tekst z systemu jest oznaczony `UntrustedText` i zamieniony
  na czysty tekst: encje dekodowane przed usunięciem znaczników, więc zakodowany tag nie wraca jako HTML.
- Argumenty: schemat egzekwuje starter (C10 `invalid_argument` i zdarzenie C8); nazwa argumentu spoza schematu
  nie trafia do logu ani wyniku, więc nie da się podrobić linii audytu (test `anArgumentNameCannotForgeAnAuditLine`).
- Rozliczalność: notatka zapisana kontem serwisowym ma na początku linię z nazwą użytkownika z tokenu
  gateway'a (README, "Autor notatki przy koncie serwisowym").
- Profil `dev` (klucze testowe, bez mTLS) jest w jarze, ale wstaje tylko na adresie pętli zwrotnej (starter,
  test `DevProfileLoopbackTest`). Serwer, do którego łączy się gateway, działa wyłącznie w konfiguracji bazowej.
- Serwer nie trzyma poświadczeń. Konto serwisowe service desku (`service-account`, D10) przychodzi przez C3 jak
  każde inne. To jedyny typ, który serwer przyjmuje, więc granicą autoryzacji są granty kolejek z paczki polityk
  (`QueueGrants`, README "Granica kolejek"); konfiguracja serwera nie otwiera żadnej kolejki.
  Zgłoszenie spoza grantów odpada przed odczytem jego kolejki, żeby liczba wywołań nie zdradzała, że istnieje.
- Ścieżki API pochodzą z konfiguracji walidowanej przy starcie; argumenty idą tylko jako zmienne URI kodowane
  ściśle albo wartości JSON. Treść notatki dostaje stałe pola z konfiguracji (np. widoczność wewnętrzna).
- Hinty MCP (`readOnlyHint`, `destructiveHint`) są informacją dla klienta, nie zabezpieczeniem.

## Wdrożenie

Konto bez uprawnień, sandbox systemd (`deploy/systemd/mcp-servicedesk.service`), sekrety tylko w pliku
środowiska (0640, root:mcp) albo z magazynu sekretów, certyfikat serwera z SAN `mcp-servicedesk.internal` z CA
wewnętrznego. Redis: użytkownik ACL `mcp-servicedesk` z kluczami `jti:mcp-servicedesk:*` i komendą `SET`.
