# STATUS - mcp-server-servicedesk (ws-04)

M1: osiągnięty (2026-10-27, runda 2; 2026-10-28 przeniesiony na starter 3.0.0-alpha-10) - `search_tickets`, `get_ticket`, `add_note` na generycznym adapterze REST (D10); konto serwisowe fail-closed (tylko kolejki z konfiguracji, pusta lista = odmowa), notatki z autorem zlecenia, walidacja argumentów w starterze (C10 + C8), zmiany template'u od 7f65f8f przeniesione, 84 testy na stubie.

Start: wygenerowany z `mcp-server-template` 7f65f8f (`./new-server.sh servicedesk`), pierwszy commit to stan
bez zmian. Starter: BOM `pl.aiwcorpo.mcp:mcp-platform-bom:3.0.0-alpha-10` (kontrakty 2.1.1). M2 nie jest
zaczęty.

## Runda 2: znalezisko -> status

| Znalezisko | Status |
| --- | --- |
| alpha-6: walidacja argumentów w starterze, brak własnych obejść SDK | zrobione: serwer nie ma customizera MCP; `ServerTestSupport.assertInvalidArgument` wymaga C10 `invalid_argument` i odrzuca tekst SDK `input validation failed`; `GATEWAY.asGateway(user, credential, Type)` zamiast nadpisywania nagłówka |
| alpha-6: `@ArgumentLimits` + `SchemaLimitedToolCallbacks`, `contractPattern` | nie zastosowane świadomie (decyzja koordynatora): ręczne schematy zostają, bo są zamknięte (`type: object`, `additionalProperties: false`). Wzorce nadal pochodzą z kopii w `Identifiers`; przejście na `@Tool` + `contractPattern` usunie kopię, gdy będzie potrzebne |
| S-H1 (nazwa argumentu wstrzykuje linię audytu) | zamknięte starterem alpha-6; test serwera `GetTicketToolTest.anArgumentNameCannotForgeAnAuditLine`: `invalid_argument`, zdarzenie C8, żadnej linii zaczynającej się od sfałszowanego JSON w stdout |
| S-M1 (HTML po dekodowaniu encji) | poprawione: `PlainText` dekoduje encje przed usunięciem znaczników i powtarza usuwanie do skutku; test, który oczekiwał `<script>`, zmieniony; nowe testy kodowanych tagów, `<<b>script>` i braku tagu w wyniku |
| S-M2 (notatka konta serwisowego bez autora) | poprawione: przy `service-account` treść zaczyna się od `[Added through the MCP platform on behalf of <usr>]`, nazwa z `CallerContext` (bez argumentów, tylko `A-Za-z0-9._@-`, inaczej `unknown`); README "Autor notatki przy koncie serwisowym"; test w `AddNoteToolTest`, brak prefiksu w `UserSecretModeTest` |
| S-L1 (TOCTOU odczyt -> zapis) | zostaje, opis w "Odstępstwa" |
| S-L2 (mutacja `hit.queue().equals(queue)`) | test `ServicedeskServiceTest.searchDropsHitsOfAnotherAllowedQueueToo` (mutacja zabita) i trafienie `OPS-5` indeksowane też pod `IT` w stubie |
| S-L3 (pola stałe notatki) | częściowo: pointery pól stałych nie mogą się pokrywać ani zawierać (z treścią notatki i między sobą), test; wymóg co najmniej jednego pola stałego w trybie `service-account` nie zrobiony (zmienia kontrakt konfiguracji, do decyzji przy wyborze produktu) |
| S-L4 i wspólne: `http` poza dev, brak limitu rozmiaru | poprawione zmianami template'u: `https` poza `dev` (`MCP_SERVICEDESK_ALLOW_PLAIN_HTTP`), limit 1 MB, 3xx jako błąd |
| CC-072 `UntrustedText` | zastosowane dla `subject` i `description`: `{kind, format: "text/plain", text, truncated}`; status, priorytet i daty zostają zwykłymi polami (krótkie, do 64 znaków, bez znaków sterujących) |
| CC-076 (servicedesk fail-closed) | już tak jest |
| Template od 7f65f8f | przeniesione: pom, `application.yml` (limit, `allow-plain-http`, tylko `health`), `application-dev.yml`, `BackendResponseException`, `BoundedBodyInterceptor`, kolejność `catch` w adapterze, systemd, `install.sh`, `ProductionConfigurationTest`, `DevProfileLoopbackTest`, stub z rekordami BADJSON/302/SLOW/HUGE, README (uruchomienie lokalne i mTLS), SECURITY.md, `jak-napisac-narzedzie.md` |

Weryfikacja: `./mvnw -B clean verify` (JDK 25) -> 79 testów, 0 błędów. Uruchomienie wg README: profil `dev` na
18530 (health 200, `POST /mcp` bez JWT 401, `/actuator/prometheus` bez dostępu), konfiguracja bazowa z mTLS
`mcp-platform-dev` na 18530 (health po mTLS z certyfikatem gateway'a 200, `POST /mcp` bez JWT 401, bez
certyfikatu klienta handshake zerwany, ostrzeżenie `backend_plain_http` przy jawnej zgodzie). Procesy zatrzymane.

## Migracja na starter 3.0.0-alpha-10 (2026-10-28)

BOM alpha-6 -> alpha-10 (kontrakty 2.1.1). Źródło: `MIGRATION-3.0.md`, alpha-7 do alpha-10. M2 nie jest zaczęty.

| Punkt | Status |
| --- | --- |
| Limity na każdym poziomie, `contractPattern` zamiast ręcznych schematów (S2-L3) | zrobione: `ServicedeskToolSchemas` i ręczne budowanie callbacków usunięte, `McpToolsConfiguration` to `SchemaLimitedToolCallbacks.of(...)`; argumenty z `@ArgumentLimits` (`contractPattern` `ServiceDeskQueueId`/`ServiceDeskTicketId`, `minLength`/`maxLength`). Decyzja koordynatora z rundy 2 ("ręczne schematy zostają") uchylona zadaniem. `Identifiers` czyta wzorce z `common.schema.json` zamiast kopii; `ToolDefinitionsTest` sprawdza wzorzec w schemacie |
| Wynik jako rekordy z `UntrustedText` startera, `@Nullable` | zrobione: `Ticket` i `TicketSummary` niosą `UntrustedText` (`mcp-core` zależy od startera dla tego typu), `@Nullable` na temacie, opisie, statusie, priorytecie i datach; test `aTicketWithEmptyOptionalFieldsIsReturnedWithNulls` |
| Kolejność: konwersja, `normalize`, `cut`, filtr, `plainCut` | `PlainText`: dekodowanie encji i usunięcie znaczników (konwersja HTML), `TextHygiene.normalize`, tidy białych znaków, `UntrustedText.cut`, `plainCut`. Znak niewidoczny, także zapisany encją, daje spację. Znacznik ` ...[cut]` usunięty, `truncated` z cięcia (S2-L1 zamknięte); `aTextThatEndsLikeTheOldCutMarkIsNotFlagged` |
| Spójna obsługa treści (wspólne 1 z rundy 2) | servicedesk: HTML -> tekst, bez escapowania, klient nie renderuje jako HTML |
| `tool_failed` domenowe na INFO | `ToolFailureLogLevelTest`; testów oczekujących ERROR nie było |
| `dev` i obejścia w `application-dev.yml` | usunięte `replay-store: memory` i `mtls.required: false`; `SPRING_PROFILES_ACTIVE=dev` zostaje tylko w poleceniu README |
| Własne beany polityki i audytu | brak |
| Klient HTTP odrzuca query i fragment w adresie | `ServicedeskApi.requireBaseUrl` już to robiło (rzut przy starcie adaptera); dopisane testy dla `?`, `#frag`, `?x=1#frag` |
| S2-L2 test filtra nazwy autora notatki | `anAuthorNameOutsideTheSafeAlphabetIsNeverWrittenIntoTheNote` (nazwa z `]` i znacznikiem trafia do `sub`, nie do notatki). Druga linia ze znacznikiem: zamknięte w rundzie 4 (S3-L1) |
| Claimy `vs` i `vsc:{}` | `GatewayLinkClaimsTest`: wywołanie `get_ticket` z `virtualServer("it-helpdesk", Map.of())` przechodzi |
| Zgodność z mockiem ws-01 | nie sprawdzano (STATUS ws-01 nie obiecuje mocka z kształtem pól tego generycznego adaptera) |

Weryfikacja: `./mvnw -B clean verify` (JDK 25) -> 86 testów, 0 błędów (mcp-app 50, mcp-integration 19, mcp-application 8,
mcp-core 6, mcp-tools 3). Uruchomienie wg README: `dev` na 18530, health 200, `POST /mcp` bez JWT 401; proces zatrzymany.

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
- Treść z systemu (temat, opis) jako `UntrustedText` (CC-072), bez HTML, skryptów i znaków sterujących; encje dekodowane przed usunięciem znaczników.
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
- Testy: `./mvnw verify` (JDK 25, bez Dockera) -> 79 testów, 0 błędów: core 6, application 8, integration 16,
  tools 4, app 45 (`SearchTicketsToolTest` 7, `GetTicketToolTest` 12, `AddNoteToolTest` 7, `QueueBoundaryTest` 5,
  `UserSecretModeTest` 3, `ToolDefinitionsTest` 3, `GatewayOnlyAccessTest` 3, `ProductionConfigurationTest` 3,
  `NoAllowedQueuesTest` 1, `DevProfileLoopbackTest` 1). Testy autoryzacji: zgłoszenie z innej kolejki,
  przeniesione z dozwolonej do niedozwolonej (odczyt, notatka, wyszukiwanie z nieaktualnym indeksem),
  przeniesione do dozwolonej, alias, bez kolejki, nieistniejące, 403, wariant wielkości liter kolejki, token
  użytkownika w trybie konta serwisowego, pusta lista.
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

- Stuby zamiast mocka z ws-01: brief chce testów na mockach ws-01, mocka service desku nie ma. `StubBackend`
  odpowiada w układzie referencyjnym adaptera, nie w API konkretnego produktu (produktu nie wybrano, D10);
  `TicketJsonTest` pokazuje drugi, zagnieżdżony kształt (numery jako liczby).
- Wynik zapisu notatki (S-L1): odczyt i zapis to dwa wywołania systemu; zgłoszenie przeniesione między nimi dostanie
  notatkę (okno TOCTOU), a po zapisie nie ma ponownego odczytu z alarmem przy zmianie kolejki. Generyczne REST nie
  daje warunkowego zapisu; przy wyborze produktu: `If-Match`/ETag albo zapis przez endpoint kolejki.
- Status, priorytet i daty w wyniku to zwykłe pola (do 64 znaków, bez znaków sterujących), nie `UntrustedText`.
- Limity w opisach narzędzi są stałe (20 trafień), bo opis wchodzi do `definitionHash`.
- Tekst wyszukiwania idzie jako wartość parametru; jeśli wybrany produkt traktuje ten parametr jako język
  zapytań (np. JQL/AQL), adapter musi dostać escapowanie właściwe dla produktu. Do sprawdzenia przy D10.
- Manifest C6, `@ResourceRef`, `ResultFilter`, Cedar: M2/M3, nie zaczęte.

## Czego potrzebuje ws-01

- Usługa `mcp-servicedesk` w compose potrzebuje `MCP_SERVICEDESK_ALLOW_PLAIN_HTTP: "true"`, dopóki mock jest po
  `http` (poza `dev` serwer przyjmuje tylko `https`).
- Mocka service desku w `mcp-platform-dev` nie ma. Propozycja: WireMock w układzie referencyjnym z
  `application.yml` (`/api/v1/tickets/{id}`, `/api/v1/queues/{queue}/tickets?q=&limit=`,
  `POST /api/v1/tickets/{id}/notes`), klucz API konta serwisowego sprawdzany jak PAT w mocku Jiry, dane: kolejki
  `IT`, `OPS`, `FIN` (ta ostatnia niedozwolona), zgłoszenie przeniesione między kolejkami. Wzór odpowiedzi:
  `mcp-app/src/test/java/.../StubBackend.java`. Po wyborze produktu (D10) mock w jego API.
- Usługa `mcp-servicedesk` w compose: `MCP_CONNECT_URL` (jak w STATUS template'u), `MCP_SERVICEDESK_ALLOWED_QUEUES`
  i `MCP_SERVICEDESK_CREDENTIAL_MODE`; do sprawdzenia, jak w dev zapisać w brokerze poświadczenie `service-account` dla
  `servicedesk` (C4); nie sprawdzałem, czy `make pat` to obsługuje.

## Czego potrzebuje ws-02

Nic blokującego. Z listy po alpha-5 zamknięte w alpha-6: walidacja `inputSchema` w starterze (C10 + C8), parametr
typu poświadczenia w `TestGateway.asGateway`, `@ArgumentLimits` ze startera. Do rozważenia: `SchemaLimitedToolCallbacks`
mogłoby przyjmować gotowy `inputSchema` (ręczne schematy servicedesku nie przechodzą przez adnotacje), bo przy
`contractPattern` serwer i tak nie kopiowałby wzorców.

## Czego potrzebuje ws-03

- Template generuje `ArgumentLimits` i `SchemaLimitedToolCallbacks` w `mcp-tools`; od alpha-6 są w starterze
  (`pl.aiwcorpo.mcp.platform.input`), więc template powinien je stamtąd brać. Servicedesk ich nie używa (ręczne schematy).

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

## Runda 4: poprawki przeglądu alpha-10 (2026-10-28)

| Znalezisko | Status | Dowód |
| --- | --- | --- |
| S3-L1 podrobiona druga linia atrybucji w notatce (poprawka znaleziska MEDIUM: niepelny dekoder encji w `PlainText`) | poprawione u zrodla, bez dekodera po naszej stronie. Kierunek: treść od modelu jest wysyłana do systemu escapowana (`PlainText.escapedForNote`: `&`, `<`, `>` oraz ich formy zgodnościowe, np. pełnej szerokości, na `&amp;` `&lt;` `&gt;`), więc system pokazuje encje i tagi doslownie i nie zamienią się w znaki; prefiks atrybucji (nasz, z bezpiecznego alfabetu) nie jest escapowany. Sprawdzenie frazy `Added through the MCP platform` (`ServicedeskResourceAdapter.addNote`, przed jakimkolwiek wywołaniem) jest fail-closed i nie zakłada, że system dekoduje dokładnie raz: notatka jest odrzucana, gdy fraza występuje (1) w surowym tekście, (2) po pełnym dekodowaniu HTML5 (jsoup 1.21.2: `Parser.unescapeEntities` i `Jsoup.parse(..).wholeText()`, encje także bez średnika, tagi usunięte, powtarzane do 5 rund lub punktu stałego, sprawdzana każda forma pośrednia), (3) po zredukowaniu składni linków i obrazów Markdown (`[x](y)`, `![x](y)`, `[x][ref]`) do widocznego tekstu, każda z tych postaci osobno. Każda postać przechodzi przez `TextHygiene.normalize` i NFKC, po odrzuceniu wszystkiego poza literami i cyframi (wielkość liter, odstępy, interpunkcja, Markdown, znaki niewidoczne i default-ignorable, w tym U+00AD i U+2060..2064). Escapowanie obejmuje też składnię Markdown (`\\ [ ] ( ) ! ` * _` dostają backslash: `[x](y)` dociera jako `\\[x\\]\\(y\\)`, linia `[r]: url` nie jest definicją referencji), więc nic, co dociera do systemu, nie zamieni się w link, obraz, definicję ani blok kodu. Regex Markdown w kontroli frazy zostaje jako dodatkowa siatka (nie jest już jedyną obroną, jego niedokładność nie otwiera obejścia). Model zagrożenia: atrybucja jest chroniona escapowaniem całej składni (HTML i Markdown) oraz fail-closed kontrolą frazy na surowym, zdekodowanym i zredukowanym z linków tekście; pozostałe ryzyko: homoglify liter z innych alfabetów (np. cyrylica), nieobjęte, oraz autolinki URL, które nie dzielą frazy. Usunięty ręczny, niepełny dekoder encji w `contains`; doszło usuwanie znaków default-ignorable (także wypełniacze Hangul, które Unicode liczy za litery) po NFKC. Dlaczego nie typ treści: API (mock `servicedesk.py`, `servicedesk.api.note`) zna tylko pole z treścią i stałe pola, bez znacznika formatu. Założenie do potwierdzenia na prawdziwym systemie: notatka jest tam renderowana jako HTML; system pokazujący czysty tekst wyświetli `&amp;` itd. doslownie (wtedy trzeba flagi w konfiguracji). Skutek uboczny: `&` w notatce rośnie do 5 znaków, więc limit treści w systemie (mock: 10200) może zadziałać przy notatce bliskiej 10000 znaków z samymi `&` | `PlainTextTest.theAttributionPhraseIsFoundInEveryDisguiseThatSurvivesEscaping` (pełna szerokość, escapy, U+00AD, U+2060..2064, odstępy), `entitiesAndTagsAreNotDecodedSoTheyCannotFormThePhrase` (`&#101` bez średnika, `&period;`, `&amp;lt;`, tagi, `&nbsp;`: nie tworzą frazy i wychodzą escapowane), `thePhraseIsFoundAfterFullHtmlDecodingEvenWithoutSemicolonsOrAfterSeveralRounds` (encje bez średnika, nazwane, podwójne i potrójne kodowanie, tagi, komentarz), `thePhraseIsFoundWhenMarkdownLinkOrImageSyntaxSplitsIt`, `aLegitimateNoteIsNotMistakenForThePhrase`, `markdownSyntaxInANoteIsEscapedSoNothingBecomesALinkImageOrDefinition`, `AddNoteToolTest.aMarkdownLinkInANoteArrivesAsLiteralText`, `aForgedAttributionSplitByALinkIsRejected`, `aNoteIsEscapedSoTheServiceDeskShowsItLiterally`, `AddNoteToolTest.aForgedSecondAttributionLineNeverReachesTheServiceDesk` (brak POST), `markupInANoteReachesTheServiceDeskEscapedSoItIsShownLiterally`, `addsAnInternalNoteAfterReadingTheTicket` (legalna notatka z `<`, `>`, `&` trafia do systemu escapowana) |
| A10-M1 `DevProfileLoopbackTest` | bez zmian, rozwiąże starter alpha-11 | |
