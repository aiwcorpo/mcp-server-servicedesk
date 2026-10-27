# mcp-servicedesk: serwer MCP platformy

Serwer MCP dla service desku, zbudowany na `mcp-platform-starter` 3.0.0-alpha-5 (Java 25, Spring Boot 4.1.0,
Spring AI 2.0.0, kontrakty `mcp-contracts` 2.0.2). Stoi wyłącznie za gateway'em platformy: przyjmuje ruch tylko
po mTLS z wewnętrznym JWT gateway'a (C2), a do service desku woła z poświadczeniem, które gateway dołącza do
każdego żądania (C3). Bez poprawnego JWT odpowiada 401, nie ma trybu anonimowego.

Produkt service desku nie jest jeszcze wybrany (D10), więc adapter jest generycznym klientem REST: ścieżki,
parametry, treść notatki i położenie pól w JSON pochodzą z konfiguracji (`servicedesk.api`, sekcja "Adapter
REST"). Poświadczenie to `service-account` (domyślnie, klucz API konta platformy) albo `user-secret` (token
użytkownika). Przy koncie serwisowym service desk nie zna użytkownika i to serwer jest główną granicą dostępu:
obsługuje tylko kolejki jawnie wpisane w konfiguracji, a bez tej listy odmawia wszystkiego.

Logowania użytkowników, decyzji o dostępie do narzędzi i potwierdzeń akcji serwer nie robi: to zadania
gateway'a. Serwer odpowiada za narzędzia, adapter, granicę kolejek, poprawne błędy (C10) i audyt (C8), który
dokłada starter.

## Szybki start

```bash
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@25/25.0.4.1/libexec/openjdk.jdk/Contents/Home
./mvnw verify                 # build, testy przez TestGateway i atrapę backendu; jar: mcp-app/target/mcp-servicedesk.jar
```

Testy nie potrzebują Dockera ani środowiska `mcp-platform-dev`: `TestGateway` z `mcp-platform-test` wystawia
JWKS z kluczy testowych kontraktów i podpisuje wewnętrzne JWT, a `StubBackend` (test) udaje API systemu.

## Moduły i miejsce każdej rzeczy

| Moduł | Co w nim jest | W tym serwerze |
| --- | --- | --- |
| `mcp-core` | domena i porty, czysta Java | `Ticket`, `TicketSummary`, `TicketSearch`, `NoteReceipt`, `TicketNotFoundException`, `Identifiers` (wzorce z kontraktu), `QueueAccessPolicy` + `CredentialMode` (granica kolejek), porty `ServicedeskUseCase` i `ServicedeskResourcePort` |
| `mcp-application` | przypadki użycia (bez Springa) | `ServicedeskService`: kontrola kolejki przed i po wywołaniu systemu, filtr wyników wyszukiwania |
| `mcp-integration` | adaptery wyjściowe do systemu | `ServicedeskResourceAdapter` (REST, poświadczenie z C3, timeouty, bez przekierowań), `ServicedeskApi` + `ServicedeskApiProperties` (kształt API z konfiguracji, walidowany przy starcie), `TicketJson` (pola przez JSON pointer), `PlainText` (HTML -> tekst) |
| `mcp-tools` | narzędzia MCP, powierzchnia publiczna | `ServicedeskTool` (`search_tickets`, `get_ticket`, `add_note`), `ServicedeskToolSchemas` (inputSchema z wzorcami i limitami), `ServicedeskToolErrors` |
| `mcp-app` | bootstrap, wiring, jedyny jar, testy end-to-end | `McpToolsConfiguration` (lista narzędzi), `ApplicationWiringConfiguration`, `ServicedeskAccessProperties` |

Zależności idą do środka: `mcp-tools` -> `mcp-application` -> `mcp-core` <- `mcp-integration`; `mcp-app` składa
całość. Resolver zasobów (`@ResourceRef`, starter beta) dojdzie w `mcp-integration` obok adaptera, bo atrybuty
zasobu (projekt, klasyfikacja) pochodzą z systemu.

## Narzędzia

| Narzędzie | Ryzyko | Uprawnienie (C9) | Poświadczenie | Argumenty | Wynik |
| --- | --- | --- | --- | --- | --- |
| `search_tickets` | `READ_ONLY` | `mcp-servicedesk-read` | `service-account` albo `user-secret` (C3) | `queue` (obowiązkowa, wzorzec `ServiceDeskQueueId`), `text` 1-200 znaków | `{queue, tickets[{id, queue, status, updated, untrustedText{subject}}], truncated}`, najwyżej 20 |
| `get_ticket` | `READ_ONLY` | `mcp-servicedesk-read` | jw. | `ticketId` (wzorzec `ServiceDeskTicketId`) | `{id, queue, status, priority, created, updated, untrustedText{subject, description}}` |
| `add_note` | `UPDATE` | `mcp-servicedesk-write` | jw. | `ticketId`, `body` 1-10000 znaków | `{ticketId, noteId}` (`noteId` null, gdy system go nie zwraca) |

Klient widzi nazwy z prefiksem `servicedesk__` (dokłada go gateway, C5). Wzorce identyfikatorów są z
`mcp-contracts` 2.0.2 (`^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$`), te same w `inputSchema`, w walidacji narzędzia, w
konfiguracji kolejek i przy wartościach zwracanych przez system.

Błędy (C10): `not_found_or_denied` z jednym tekstem dla zgłoszenia lub kolejki nieistniejącej, ukrytej przez
system (404, 403) i spoza dozwolonych kolejek; `credential_required` przy braku poświadczenia i przy 401 z
systemu (także 401 na zapisie notatki); `invalid_argument`; `upstream_unavailable` (system nieosiągalny, 502-504);
`internal_error` (inne błędy systemu, nieczytelna odpowiedź). Argumenty, które łamią `inputSchema` (wzorzec,
długość, brak pola, pole nadmiarowe), odrzuca już walidacja schematu w MCP SDK, zanim narzędzie się wykona
(STATUS.md, "Czego potrzebuje ws-02").

Wynik zawiera tylko pola z mapowania. Tekst wpisany przez ludzi w service desku (temat, opis) jest w osobnym
obiekcie `untrustedText`, zamieniony na czysty tekst (bez tagów, skryptów, znaków sterujących), opis obcięty do
8000 znaków ze znacznikiem `...[cut]`.

## Granica kolejek

`servicedesk.access` (zmienne `MCP_SERVICEDESK_CREDENTIAL_MODE`, `MCP_SERVICEDESK_ALLOWED_QUEUES`):

| Tryb | Lista kolejek | Zachowanie |
| --- | --- | --- |
| `service-account` (domyślny) | pusta | każde narzędzie: `not_found_or_denied`, system nie jest wołany; ostrzeżenie `event=no_allowed_queues` przy starcie |
| `service-account` | `IT,OPS` | tylko te kolejki (dokładne porównanie, wielkość liter ma znaczenie) |
| `user-secret` | pusta | decyduje konto użytkownika w systemie |
| `user-secret` | niepusta | konto użytkownika, dodatkowo zawężone do listy |

Poświadczenie oznaczone przez gateway jako `service-account` jest zawsze trzymane listą, także w trybie
`user-secret` (pomyłka w konfiguracji nie otwiera dostępu). Kolejka jest oceniana na tym, co system zwraca w
chwili wywołania, nie na tym, co podał model:

- `get_ticket`: pobiera zgłoszenie, sprawdza, że system zwrócił to samo `id` (inne `id`, np. alias albo
  scalenie, = odmowa) i że jego bieżąca kolejka jest dozwolona; zgłoszenie bez kolejki = odmowa;
- `add_note`: to samo co `get_ticket`, dopiero potem zapis; przy odmowie nie ma żadnego zapisu;
- `search_tickets`: kolejka z argumentu musi być dozwolona, zanim pójdzie zapytanie; z wyników zostają tylko
  zgłoszenia, które system raportuje w tej kolejce (zgłoszenia przeniesione, a nadal w indeksie, i trafienia
  z innych kolejek odpadają).

Zgłoszenie przeniesione do niedozwolonej kolejki przestaje być dostępne od następnego wywołania. Między
odczytem a zapisem notatki (dwa wywołania systemu) zostaje okno, w którym zgłoszenie może zmienić kolejkę;
generyczne REST nie daje warunkowego zapisu (STATUS.md).

## Adapter REST

Kształt API jest w `servicedesk.api` (`application.yml` ma układ referencyjny, na którym działają testy).
Dla konkretnego produktu nadpisuje się go plikiem podanym w `--spring.config.additional-location`.
Konfiguracja jest walidowana przy starcie: ścieżki tylko z liter, cyfr, `/_.-` i wymaganych placeholderów,
bez `..`, `?`, `#`; nazwy parametrów i nagłówka z zamkniętego alfabetu; JSON pointery bez escapowania; zła
konfiguracja = brak startu.

| Klucz | Znaczenie | Referencyjnie |
| --- | --- | --- |
| `credential-header` | nagłówek z poświadczeniem: `Authorization` dostaje `<scheme> <value>` z C3, inny nagłówek (np. `X-API-Key`) samą wartość | `Authorization` |
| `ticket-path` | zgłoszenie, placeholder `{ticketId}` | `/api/v1/tickets/{ticketId}` |
| `search.path`, `search.queue-param` | wyszukiwanie; kolejka jako `{queue}` w ścieżce albo parametr | `/api/v1/queues/{queue}/tickets` |
| `search.text-param`, `search.limit-param` | parametr tekstu (wartość, nie język zapytań) i limitu (serwer prosi o 21) | `q`, `limit` |
| `search.results-pointer` | tablica trafień w odpowiedzi; puste = odpowiedź jest tablicą | `/items` |
| `note.path`, `note.method` | zapis notatki, `{ticketId}`; POST, PUT albo PATCH | `/api/v1/tickets/{ticketId}/notes`, `POST` |
| `note.body-pointer` | gdzie w JSON trafia treść notatki | `/body` |
| `note.static-fields` | stałe pola każdej notatki (`pointer` + literał JSON), np. widoczność wewnętrzna, żeby notatka nie poszła do klienta zgłoszenia przez domyślne ustawienie produktu | `/visibility` = `"internal"` |
| `note.id-pointer` | id notatki w odpowiedzi; puste = brak | `/id` |
| `fields.*` | JSON pointery pól zgłoszenia: `id`, `queue` (wymagane), `subject`, `status`, `priority`, `created`, `updated`, `description` (puste = pole pomijane) | `/id`, `/queue`, ... |

Przykład dla API zagnieżdżonego (numery zgłoszeń i kolejek jako liczby, klucz API we własnym nagłówku):

```yaml
servicedesk:
  api:
    credential-header: X-API-Key
    ticket-path: /rest/requests/{ticketId}
    search: {path: /rest/requests, queue-param: queueId, text-param: searchTerm, limit-param: limit, results-pointer: /values}
    note:
      path: /rest/requests/{ticketId}/comments
      body-pointer: /body
      static-fields: [{pointer: /public, value: 'false'}]
      id-pointer: /id
    fields: {id: /issueId, queue: /serviceDesk/queueId, subject: /fields/summary, status: /currentStatus/status,
             created: /createdDate/iso8601, description: /fields/description}
```

Tekst wyszukiwania i identyfikatory idą wyłącznie jako zmienne URI kodowane ściśle (`&`, `/`, `#`, `+` nie
zmieniają ścieżki ani nie dokładają parametrów); treść notatki jako wartość JSON. Odpowiedź czytana jest jako
JSON tylko przez skonfigurowane pointery; reszta pól (dane zgłaszającego, pola wewnętrzne) nie wychodzi poza
adapter. Przekierowania 3xx nie są śledzone (`internal_error`).

## Jak przechodzi wywołanie

```
gateway --mTLS (CN mcp-gateway) + JWT C2 (aud mcp-servicedesk, tool add_note) + X-MCP-Credential(-Type)-->
  starter: InternalJwtFilter (inaczej 401) -> CredentialHeaderFilter -> AuditingToolCallback (C8)
  -> MCP SDK: walidacja argumentów względem inputSchema
  -> ServicedeskTool.addNote: walidacja ticketId i body
  -> ServicedeskService: QueueAccessPolicy (tryb + typ poświadczenia), GET zgłoszenia, kontrola id i kolejki
  -> ServicedeskResourceAdapter: CredentialContext.require("servicedesk"), POST notatki
```

## Konfiguracja

Konfiguracja bazowa (`mcp-app/src/main/resources/application.yml`) jest produkcyjna: mTLS, JWKS gateway'a po
TLS z CA wewnętrznym, `jti` w Redisie. Profil `dev` wyłącza tylko TLS i służy testom; pozostałe ułatwienia
(klucze testowe, `jti` w pamięci, `mtls.required=false`) daje `TestGateway`, a starter odrzuca je poza `dev`.

| Zmienna | Znaczenie | Domyślnie |
| --- | --- | --- |
| `MCP_TLS_KEYSTORE`, `MCP_TLS_TRUSTSTORE`, `MCP_TLS_STORE_PASSWORD` | PKCS12: certyfikat serwera (SAN `mcp-servicedesk.internal`) i CA wewnętrzne; truststore służy też do JWKS | wymagane |
| `MCP_GATEWAY_JWKS_URI` | JWKS wewnętrznego JWT, np. `https://mcp-gateway.internal:8443/.well-known/jwks.json` | wymagane |
| `MCP_GATEWAY_ISSUER` | `iss` C2 | `https://mcp-gateway.internal` |
| `MCP_CONNECT_URL` | strona portalu do połączenia konta, z `{system}`; trafia do `credential_required` | wymagane |
| `MCP_SERVICEDESK_URL` | adres bazowy API service desku (bez query i fragmentu) | wymagane |
| `MCP_SERVICEDESK_SSL_BUNDLE` | bundle SSL dla HTTPS systemu, np. `mcp-internal-ca`; puste = zaufanie JDK | puste |
| `MCP_SERVICEDESK_CREDENTIAL_MODE` | `service-account` albo `user-secret` (sekcja "Granica kolejek") | `service-account` |
| `MCP_SERVICEDESK_ALLOWED_QUEUES` | dozwolone kolejki, po przecinku; w trybie `service-account` bez nich wszystko jest odmawiane | puste |
| `MCP_REDIS_HOST`, `MCP_REDIS_PORT`, `MCP_REDIS_USER`, `MCP_REDIS_PASSWORD` | Redis dla jednorazowego `jti` (użytkownik ACL `mcp-servicedesk`, klucze `jti:mcp-servicedesk:*`) | `localhost`, `6379`, `mcp-servicedesk`, puste |
| `MCP_PORT`, `MCP_BIND` | port i adres nasłuchu | `8443`, `0.0.0.0` |

Endpoint MCP to `/mcp` (domyślne ustawienie startera, tak jak oczekuje gateway: `internal-url:
https://mcp-servicedesk.internal:8443/mcp`). Bez JWT odpowiada tylko `/actuator/health` (i `liveness`,
`readiness`), ale TLS i tak wymaga certyfikatu klienta z CA wewnętrznego.

Serwer nie wstaje bez `MCP_GATEWAY_JWKS_URI`, `MCP_CONNECT_URL`, `MCP_SERVICEDESK_URL` i plików TLS.

Na hoście ze środowiskiem `mcp-platform-dev` (certyfikat i użytkownik Redisa `mcp-servicedesk` są w
środowisku; mocka service desku tam nie ma, więc `MCP_SERVICEDESK_URL` wskazuje dowolny port, a wywołania
narzędzi kończą się `upstream_unavailable`):

```bash
dev=../mcp-platform-dev
export MCP_TLS_STORE_PASSWORD="$(grep '^TLS_STORE_PASSWORD=' $dev/.secrets/dev.env | cut -d= -f2-)"
export MCP_TLS_KEYSTORE=$dev/certs/mcp-servicedesk/keystore.p12 MCP_TLS_TRUSTSTORE=$dev/certs/ca/truststore.p12
export MCP_PORT=18530 MCP_BIND=127.0.0.1 MCP_REDIS_PORT=16379 MCP_SERVICEDESK_URL=http://localhost:18531
export MCP_REDIS_PASSWORD="$(grep '^REDIS_MCP_SERVICEDESK_PASSWORD=' $dev/.secrets/dev.env | cut -d= -f2-)"
export MCP_SERVICEDESK_ALLOWED_QUEUES=IT
export MCP_GATEWAY_JWKS_URI=https://localhost:18445/.well-known/jwks.json
export MCP_CONNECT_URL='http://localhost:18090/connections?system={system}'
java -jar mcp-app/target/mcp-servicedesk.jar
curl --cacert $dev/certs/ca/ca.crt --cert $dev/certs/mcp-gateway/tls.crt --key $dev/certs/mcp-gateway/tls.key \
     --resolve mcp-servicedesk.internal:18530:127.0.0.1 https://mcp-servicedesk.internal:18530/actuator/health   # 200
```

`POST /mcp` bez JWT daje 401, połączenie bez certyfikatu klienta jest zrywane w handshake'u.

## Testy

`./mvnw verify` (JDK 25, bez Dockera): 66 testów.

| Klasa | Co sprawdza |
| --- | --- |
| `SearchTicketsToolTest` | zapytanie budowane przez serwer (`GET /api/v1/queues/IT/tickets?q=...&limit=21`), tekst jako zakodowana wartość, wyniki tylko z kolejki, brak pól spoza mapowania; `credential_required` (brak i 401); nieznana kolejka -> `not_found_or_denied`; złe argumenty nie docierają do systemu |
| `GetTicketToolTest` | poświadczenie z C3 dociera do systemu; HTML i pola dodatkowe nie wychodzą; brak poświadczenia i 401; 404 = 403; złe `ticketId` (także pole nadmiarowe) nie docierają do systemu; poświadczenie i treść błędu systemu nie trafiają do wyniku ani logu |
| `AddNoteToolTest` | odczyt zgłoszenia, potem POST z notatką wewnętrzną; `UPDATE` bez `readOnlyHint`/`destructiveHint`; brak poświadczenia, 401 na odczycie i na zapisie; 404, 403 i 403 na zapisie dają ten sam błąd; złe argumenty |
| `QueueBoundaryTest` | konto serwisowe: zgłoszenie z innej kolejki, alias, bez kolejki, 403 i nieistniejące dają bajtowo ten sam błąd w `get_ticket` i `add_note`, bez żadnego zapisu; zgłoszenie przeniesione z dozwolonej kolejki przestaje być dostępne (także w wyszukiwaniu z nieaktualnym indeksem), przeniesione do dozwolonej jest dostępne; wyszukiwanie w niedozwolonej kolejce (także `it` zamiast `IT`) nie dociera do systemu; poświadczenie użytkownika nie poszerza trybu `service-account` |
| `NoAllowedQueuesTest` | tryb `service-account` bez listy: każde narzędzie `not_found_or_denied`, zero wywołań systemu |
| `UserSecretModeTest` | tryb `user-secret`: token użytkownika sięga każdej kolejki, którą pokazuje system; poświadczenie oznaczone `service-account` bez listy jest odrzucane |
| `ToolDefinitionsTest` | dokładnie trzy narzędzia z ryzykiem; `inputSchema` zamknięte (`additionalProperties: false`), wzorce z kontraktu, limity długości; opisy bez instrukcji |
| `GatewayOnlyAccessTest` | bez JWT 401 (`tools/call`, `ping`, `initialize`), JWT innego serwera, innego narzędzia, wariant serwisowy i przeterminowany -> 401; bez JWT tylko health |
| `ServicedeskServiceTest`, `QueueAccessPolicyTest` | granica kolejek bez Springa |
| `ServicedeskToolTest` | walidacja w narzędziu bez schematu SDK przed nią (`InvalidToolArgumentException`) |
| `ServicedeskApiTest`, `TicketJsonTest`, `PlainTextTest` | kodowanie URI, treść notatki, walidacja konfiguracji, dwa różne kształty API, HTML -> tekst |

`StubBackend` (test) to serwer HTTP udający service desk w układzie referencyjnym; w `mcp-platform-dev` nie ma
mocka service desku.

## Dokumentacja

- `docs/jak-napisac-narzedzie.md`: zasady i kolejność kroków przy nowym narzędziu.
- `docs/architecture/overview.md`: architektura i decyzje.
- `docs/security/SECURITY.md`: niezmienniki bezpieczeństwa w tym serwerze.
- `docs/api/tools-catalog.md`: rejestr narzędzi.
- `docs/operations/deployment-vm-systemd.md`, `deploy/`, `Dockerfile`: wdrożenie.
