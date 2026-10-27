# STATUS - mcp-server-servicedesk (ws-04)

M1: osiągnięty (2026-10-27) - `search_tickets`, `get_ticket`, `add_note` na generycznym adapterze REST (D10) i starterze 3.0.0-alpha-5; konto serwisowe fail-closed (tylko kolejki z konfiguracji, pusta lista = odmowa), 66 testów na stubie.

Start: wygenerowany z `mcp-server-template` 7f65f8f (`./new-server.sh servicedesk`), pierwszy commit to stan
bez zmian. Starter: BOM `pl.aiwcorpo.mcp:mcp-platform-bom:3.0.0-alpha-5` (kontrakty 2.0.2). M2 nie jest
zaczęty.

## Co działa

- Narzędzia (README, "Narzędzia"; `docs/api/tools-catalog.md`):
  - `search_tickets(queue, text)`, `READ_ONLY`: kolejka obowiązkowa, zapytanie składa serwer (ścieżka i nazwy
    parametrów z konfiguracji, tekst jako zakodowana wartość, limit 20, serwer prosi o 21 i ustawia
    `truncated`); z wyników zostają tylko zgłoszenia, które system raportuje w tej kolejce.
  - `get_ticket(ticketId)`, `READ_ONLY`: pola z mapowania, opis zamieniony z HTML na tekst i obcięty.
  - `add_note(ticketId, body)`, `UPDATE`: najpierw odczyt i kontrola kolejki, potem zapis ze stałymi polami z
    konfiguracji (referencyjnie `visibility: "internal"`).
- Rejestracja tylko przez `ToolCallbackProvider` w `McpToolsConfiguration`; callbacki to `MethodToolCallback`
  z ręcznym `inputSchema` (`ServicedeskToolSchemas`: `additionalProperties: false`, wzorce
  `ServiceDeskQueueId`/`ServiceDeskTicketId` z kontraktu, `minLength`/`maxLength`). Te same limity sprawdza
  narzędzie (`InvalidToolArgumentException`), bo klient może pominąć schemat.
- Treść z systemu (temat, opis) w osobnym obiekcie `untrustedText`, bez HTML, skryptów i znaków sterujących.
- Granica kolejek (`QueueAccessPolicy`): tryb `service-account` (domyślny) albo `user-secret`, lista
  `MCP_SERVICEDESK_ALLOWED_QUEUES`. Konto serwisowe obsługuje tylko kolejki z listy, pusta lista = każde
  wywołanie `not_found_or_denied` bez wołania systemu (WARN `event=no_allowed_queues` przy starcie).
  Poświadczenie oznaczone `service-account` jest trzymane listą w każdym trybie. Ocena na kolejce zwróconej przez
  system w chwili wywołania; inne `id` w odpowiedzi (alias, scalenie) i zgłoszenie bez kolejki = odmowa.
- Błędy: 404/403/spoza listy/alias/bez kolejki -> jeden `not_found_or_denied` z jednym tekstem (testy porównują
  bajtowo); brak poświadczenia i 401 (także 401 na zapisie notatki) -> `credential_required`; treść błędu
  systemu nie trafia do wyniku ani logu (obejście `defaultStatusHandler` z template'u zostało, 3xx też bez
  treści); nieczytelna odpowiedź -> `internal_error` bez fragmentu odpowiedzi.
- Adapter REST z konfiguracji (`servicedesk.api`, README "Adapter REST"): ścieżki z placeholderami, kolejka w
  ścieżce albo parametrze, nazwy parametrów, nagłówek poświadczenia (`Authorization` z C3 albo np.
  `X-API-Key` z samą wartością), metoda notatki POST/PUT/PATCH, JSON pointery pól, treści notatki i id notatki.
  Konfiguracja walidowana przy starcie (zła = brak startu). URI przez `UriComponentsBuilder.encode()` + zmienne,
  więc tekst z `&`, `#`, `/`, `+` nie zmienia ścieżki ani parametrów (test).
- Testy: `./mvnw verify` (JDK 25, bez Dockera) -> 66 testów, 0 błędów: core 6, application 7, integration 14,
  tools 3, app 36 (`SearchTicketsToolTest` 7, `GetTicketToolTest` 7, `AddNoteToolTest` 7, `QueueBoundaryTest` 5,
  `UserSecretModeTest` 3, `ToolDefinitionsTest` 3, `GatewayOnlyAccessTest` 3, `NoAllowedQueuesTest` 1).
  Testy autoryzacji: zgłoszenie z innej kolejki, przeniesione z dozwolonej do niedozwolonej (odczyt, notatka,
  wyszukiwanie z nieaktualnym indeksem), przeniesione do dozwolonej, alias, bez kolejki, nieistniejące, 403,
  wariant wielkości liter kolejki, token użytkownika w trybie konta serwisowego, pusta lista.
- Smoke konfiguracji produkcyjnej na hoście (port 18530, certyfikat i użytkownik Redisa `mcp-servicedesk` z
  `mcp-platform-dev`, Redis 16379): start, `/actuator/health` po mTLS z certyfikatem gateway'a 200, `POST /mcp`
  bez JWT 401, bez certyfikatu klienta handshake zerwany. Proces zatrzymany.

## Miejsce na QueryScope i ResultFilter (beta, M3)

Jedno miejsce: `QueueAccessPolicy` (mcp-core), wołana z `ServicedeskService`:
- przed wywołaniem systemu z kolejką (`search_tickets`: `permits(queue)`; `get_ticket`/`add_note`:
  `servesAnyQueue`) -> tu wejdzie `QueryScope` (kolejki z `vsc` i grantów Cedar zawężają dozwolone);
- na każdym zgłoszeniu zwróconym przez system (`visibleTicket`, filtr wyników w `searchTickets`) -> tu wejdzie
  `ResultFilter`/ocena `ServiceDesk::Ticket in ServiceDesk::Queue` z `forResource`.
`@ResourceRef`: `queue` -> `ServiceDesk::Queue`, `ticketId` -> `ServiceDesk::Ticket` (rodzic z odpowiedzi
systemu); resolver obok adaptera w mcp-integration.

## Odstępstwa

- Argumenty łamiące `inputSchema` (wzorzec, długość, brak pola, pole nadmiarowe) odrzuca walidacja schematu w
  MCP SDK 2.0 (`ToolInputValidator`) przed narzędziem: tekst `Tool (x) input validation failed: ...` zamiast C10
  `invalid_argument` i bez zdarzenia C8 `tool_call`. Wartości argumentu w tekście nie ma (testy). Walidacja w
  narzędziu (C10) zostaje i działa dla reszty (pusty tekst, znaki sterujące) oraz bez schematu
  (`ServicedeskToolTest`). Wyłączenie walidacji SDK wymagałoby własnego `McpSyncServerCustomizer`, który
  zastępuje customizer Spring AI z `immediateExecution(true)` (pułapka opisana w `CredentialTransportGuard`),
  więc tego nie robię. Do ws-02.
- Stuby zamiast mocka z ws-01: brief chce testów na mockach ws-01, mocka service desku nie ma. `StubBackend`
  odpowiada w układzie referencyjnym adaptera, nie w API konkretnego produktu (produktu nie wybrano, D10);
  `TicketJsonTest` pokazuje drugi, zagnieżdżony kształt (numery jako liczby).
- Kształt oznaczenia danych (`untrustedText{subject, description}`) jest własny; jest otwarte CC od
  mcp-confluence (`UntrustedText` z `kind: "untrusted-data"`). Po decyzji przejdę na wspólny kształt.
- Wynik zapisu notatki: odczyt i zapis to dwa wywołania systemu; zgłoszenie przeniesione między nimi dostanie
  notatkę (okno TOCTOU). Generyczne REST nie daje warunkowego zapisu; przy wyborze produktu: `If-Match`/ETag albo
  zapis przez endpoint kolejki, jeśli produkt go ma.
- Limity w opisach narzędzi są stałe (20 trafień), bo opis wchodzi do `definitionHash`.
- Tekst wyszukiwania idzie jako wartość parametru; jeśli wybrany produkt traktuje ten parametr jako język
  zapytań (np. JQL/AQL), adapter musi dostać escapowanie właściwe dla produktu. Do sprawdzenia przy D10.
- Manifest C6, `@ResourceRef`, `ResultFilter`, Cedar: M2/M3, nie zaczęte.

## Czego potrzebuje ws-01

- Mocka service desku w `mcp-platform-dev` nie ma. Propozycja: WireMock w układzie referencyjnym z
  `application.yml` (`/api/v1/tickets/{id}`, `/api/v1/queues/{queue}/tickets?q=&limit=`,
  `POST /api/v1/tickets/{id}/notes`), klucz API konta serwisowego sprawdzany jak PAT w mocku Jiry, dane: kolejki
  `IT`, `OPS`, `FIN` (ta ostatnia niedozwolona), zgłoszenie przeniesione między kolejkami. Wzór odpowiedzi:
  `mcp-app/src/test/java/.../StubBackend.java`. Po wyborze produktu (D10) mock w jego API.
- Usługa `mcp-servicedesk` w compose: `MCP_CONNECT_URL` (jak w STATUS template'u), `MCP_SERVICEDESK_ALLOWED_QUEUES`
  i `MCP_SERVICEDESK_CREDENTIAL_MODE`; do sprawdzenia, jak w dev zapisać w brokerze poświadczenie `service-account` dla
  `servicedesk` (C4); nie sprawdzałem, czy `make pat` to obsługuje.

## Czego potrzebuje ws-02

1. Walidacja `inputSchema` w MCP SDK odpowiada przed `AuditingToolCallback`: błąd nie jest w formacie C10 i
   nie ma zdarzenia C8. Propozycja: starter dostarcza `JsonSchemaValidator`/ustawienie, które przepuszcza do
   narzędzia, a walidację schematu robi w `AuditingToolCallback` z wynikiem `invalid_argument` i audytem; albo
   bezpieczny sposób wyłączenia (`validateToolInputs(false)`) bez nadpisywania customizera Spring AI.
2. `TestGateway.asGateway` zawsze ustawia `X-MCP-Credential-Type: user-secret`; serwer z kontem serwisowym
   nadpisuje nagłówek w kliencie testowym (`ServerTestSupport.clientWithCredential`). Propozycja: parametr typu
   poświadczenia.
3. Brak sposobu na `inputSchema` z limitami przy `@Tool`; serwer buduje `MethodToolCallback` ręcznie
   (`McpToolsConfiguration.callbacks`). Wzorzec do przeniesienia do startera/template'u.

## Czego potrzebuje ws-03

- Template generuje schematy bez `pattern`/`maxLength`; po wzorcu z tego serwera (ręczne schematy) trzeba też
  opisać w `docs/jak-napisac-narzedzie.md`, że błędy schematu odpowiadają z SDK, nie z C10.

## Czego potrzebuje ws-05 / ws-09

- Przy `service-account` gateway wstrzykuje `X-MCP-Credential-Type: service-account`; serwer na tej etykiecie
  włącza listę kolejek. Polityki dla `mcp-servicedesk` muszą zakładać, że serwer bez grantu kolejki odmawia
  (CC niżej).

## Propozycje zmian kontraktu

Dopisane na końcu `mcp-contracts/CONTRACT_CHANGES.md` (status open, bez commitu):
- `CC-??? | C6`: wektor `valid-servicedesk-service-account` ma `ticketId` jako integer i `internalUrl`
  `/mcp/servicedesk`, niezgodnie z `ServiceDeskTicketId` i endpointem `/mcp`.
- `CC-??? | C7`: konto serwisowe service desku: brak grantu kolejki = odmowa, ocena na kolejce zwróconej przez
  system.
