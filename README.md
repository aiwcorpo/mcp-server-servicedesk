# mcp-servicedesk: serwer MCP platformy

Serwer MCP dla service desku, zbudowany na `mcp-platform-starter` 3.0.0-beta-10 (Java 25, Spring Boot 4.1.0,
Spring AI 2.0.0, kontrakty `mcp-contracts` 5.1.0). Stoi wyłącznie za gateway'em platformy: przyjmuje ruch tylko
po mTLS z wewnętrznym JWT gateway'a (C2), a do service desku woła z poświadczeniem, które gateway dołącza do
każdego żądania (C3). Bez poprawnego JWT odpowiada 401, nie ma trybu anonimowego.

Produkt service desku nie jest jeszcze wybrany (D10), więc adapter jest generycznym klientem REST: ścieżki,
parametry, treść notatki i położenie pól w JSON pochodzą z konfiguracji (`servicedesk.api`, sekcja "Adapter
REST"). Jedyne przyjmowane poświadczenie to `service-account` (klucz konta serwisowego, który broker wydaje gateway'owi;
`accepted-credential-types: [service-account]`, bez konfiguracji). Service desk nie zna użytkownika, więc dostęp do
kolejek wynika wyłącznie z paczki polityk: grupy `mcp-servicedesk-queue-<id>` przyznają kolejkę, a konfiguracja
serwera żadnej kolejki nie otwiera (CC-163). Wywołanie z innym typem poświadczenia
kończy się `internal_error` przed polityką i systemem.

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
| `mcp-core` | domena i porty | `Ticket`, `Queue`, `ClassifiedTicket`, `ServicedeskResourceModel`, `TicketSummary`, `TicketSearch`, `NoteReceipt`, `TicketNotFoundException`, `Identifiers` (wzorce z kontraktu), `QueueGrants` (kolejki z grantów), `ServicedeskResourceDescription` (opis dla polityki), porty `ServicedeskUseCase` i `ServicedeskResourcePort` |
| `mcp-application` | przypadki użycia (bez Springa) | `ServicedeskService`: kontrola kolejki przed i po wywołaniu systemu, wyszukiwanie ticketów i kolejek dla resolverów, filtr wyników wyszukiwania |
| `mcp-integration` | adaptery wyjściowe do systemu | `ServicedeskResourceAdapter` (REST, poświadczenie z C3, timeouty, bez przekierowań), `ServicedeskApi` + `ServicedeskApiProperties` (kształt API z konfiguracji, walidowany przy starcie), `TicketJson` (pola przez JSON pointer), `PlainText` (HTML -> tekst), `ServicedeskQueueResolver` i `ServicedeskTicketResolver` (`ResourceResolver` dla `ServiceDesk::Queue` i `ServiceDesk::Ticket`) |
| `mcp-tools` | narzędzia MCP, powierzchnia publiczna | `ServicedeskTool` (`search_tickets`, `get_ticket`, `add_note`; limity i wzorce kontraktu w `@ArgumentLimits`), `ServicedeskSearchScope` (zakres wyszukiwania z `QueryScope`), `ServicedeskToolErrors` |
| `mcp-app` | bootstrap, wiring, jedyny jar, testy end-to-end | `McpToolsConfiguration` (lista narzędzi), `ApplicationWiringConfiguration` |

Zależności idą do środka: `mcp-tools` -> `mcp-application` -> `mcp-core` <- `mcp-integration`; `mcp-app` składa
całość. Resolwery zasobów są w `mcp-integration` obok adaptera, bo atrybuty zasobu (kolejka, klasyfikacja)
pochodzą z systemu.

## Narzędzia

| Narzędzie | Tytuł | Ryzyko | Akcja Cedar | Uprawnienie (C9) | Poświadczenie | Argumenty | Wynik |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `search_tickets` | Szukaj zgłoszeń w kolejce | `READ_ONLY` | `read` | `mcp-servicedesk-read` | `service-account` (C3) | `queue` (obowiązkowa, wzorzec `ServiceDeskQueueId`, `@ResourceRef` `ServiceDesk::Queue`), `text` 1-200 znaków | `{queue, tickets[{id, queue, status, updated, subject}], truncated}`, najwyżej 20; `subject` to obiekt z oznaczeniem danych (niżej) |
| `get_ticket` | Pobierz zgłoszenie | `READ_ONLY` | `read` | `mcp-servicedesk-read` | jw. | `ticketId` (wzorzec `ServiceDeskTicketId`, `@ResourceRef` `ServiceDesk::Ticket`) | `{id, queue, status, priority, created, updated, subject, description}`; `subject` i `description` to obiekty z oznaczeniem danych (niżej) |
| `add_note` | Dodaj notatkę do zgłoszenia | `UPDATE` | `update` | `mcp-servicedesk-write` | jw. | `ticketId` (`@ResourceRef` `ServiceDesk::Ticket`), `body` 1-10000 znaków | `{ticketId, noteId}` (`noteId` null, gdy system go nie zwraca) |

Klient widzi nazwy z prefiksem `servicedesk__` (dokłada go gateway, C5). Wzorce identyfikatorów są z
`mcp-contracts` 5.1.0, czytane z kontraktu (`^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$`), te same w `inputSchema`, w walidacji narzędzia i przy wartościach zwracanych przez system.

Błędy (C10): `not_found_or_denied` z jednym tekstem (tekst startera) dla zgłoszenia lub kolejki nieistniejącej,
ukrytej przez system (404, 403), spoza przyznanych kolejek (także kolejka z argumentu `search_tickets`) i
`restricted`; to samo przy awarii systemu przy odczycie zgłoszenia (500, timeout, przekierowanie, zła odpowiedź), bo konto
serwisowe nie może po niej poznać, czy zgłoszenie istnieje (starter beta-8; przyczynę zapisują log `resolver_failed_hidden`
i audyt `upstream_unavailable`, beta-9); `access_denied` bez grupy narzędzia; `credential_required` przy braku poświadczenia i przy 401 z
systemu (także 401 na zapisie notatki); `invalid_argument`; `upstream_unavailable` (kolejka, zapis notatki i wyszukiwanie: system nieosiągalny, 502-504);
`internal_error` (inne błędy systemu, nieczytelna odpowiedź, przekierowanie, odpowiedź ponad limit rozmiaru).
Argumenty łamiące `inputSchema` (wzorzec, długość, brak pola, pole nadmiarowe) odrzuca starter jako
`invalid_argument` ze zdarzeniem audytu C8, bez wartości argumentu i bez nazw spoza schematu (ani w wyniku, ani
w logu); resztę (tekst złożony z samych spacji) narzędzie.

Wynik zawiera tylko pola z mapowania. Tekst wpisany przez ludzi w service desku (temat, opis) ma kształt
`UntrustedText` z kontraktów (CC-072): `{"kind":"untrusted-data","format":"text/plain","text":"...","truncated":false}`.
Kolejność: konwersja z HTML (najpierw dekodowane encje, potem usuwane znaczniki i skrypty, powtarzane do skutku,
więc zakodowany tag nie wraca jako HTML), `TextHygiene.normalize` (znak sterujący lub niewidoczny zastępuje
spacja, także ten zapisany encją `&#8203;`), cięcie do limitu (`UntrustedText.cut`: temat 500, opis 8000 znaków),
`plainCut`. `truncated` pochodzi z cięcia, nie z treści; w tekście nie ma znacznika cięcia. W wyniku `<` i `&`
to zwykłe znaki tekstu, bez escapowania; klient nie renderuje go jako HTML. Pola, które service desk może
zostawić puste (temat, status, opis, daty), bywają `null`.

### Autor notatki przy koncie serwisowym

Przy poświadczeniu `service-account` autorem każdej notatki w service desku jest konto platformy. Żeby zespół
service desku widział, kto ją zlecił, serwer dokleja na początku treści stałą linię
`[Added through the MCP platform on behalf of <użytkownik>]`. Nazwa pochodzi z zaufanego tokenu gateway'a
(`CallerContext`: `usr`, a gdy go brak `sub`), nie z argumentów narzędzia, i jest ograniczona do znaków
`A-Za-z0-9._@-` (inaczej `unknown`). Limit
10000 znaków dotyczy tekstu od modelu, prefiks dochodzi do niego. Treść od modelu wychodzi do systemu escapowana (`&`, `<`, `>` i ich formy zgodnościowe na encje, składnia Markdown `\ [ ] ( ) ! ` * _` z backslashem), więc system
pokazuje tagi i encje doslownie; zakłada to, że notatka jest tam renderowana jako HTML. Treść, która sama zawiera
frazę `Added through the MCP platform` (sprawdzane w surowym tekście, po pełnym dekodowaniu HTML5 z usunięciem tagów i po zredukowaniu linków Markdown, każdorazowo po usunięciu znaków niewidocznych, NFKC, interpunkcji, wielkości liter i odstępów), jest odrzucana jako `invalid_argument`,
żeby agent service desku nie widział drugiej, podrobionej atrybucji.

## Granica kolejek

Kolejki otwiera tylko paczka polityk (C7): grupy `mcp-servicedesk-queue-<id>` w `entities/static.json`. Serwer nie ma
listy kolejek ani trybu poświadczenia w konfiguracji (zmienne `MCP_SERVICEDESK_ALLOWED_QUEUES`,
`MCP_SERVICEDESK_CREDENTIAL_MODE` i `MCP_ACCEPTED_CREDENTIAL_TYPES` nie istnieją). Wywołujący bez grupy kolejki dostaje
`not_found_or_denied` z `get_ticket`, `add_note` i `search_tickets`, a system nie jest wołany. Kolejka w argumencie
spoza przyznanych to także `not_found_or_denied`, nie `access_denied`. Kolejka jest oceniana na tym, co
system zwraca w chwili wywołania, nie na tym, co podał model:

- `get_ticket`: resolver zgłoszenia pobiera je, sprawdza, że system zwrócił to samo `id` (inne `id`, np. alias albo
  scalenie, = odmowa) (zgłoszenie bez kolejki = odmowa), potem pyta o
  kolejkę (klasyfikacja); narzędzie czyta ten wynik, bez drugiego wywołania;
- `get_ticket` i `add_note`: bieżąca kolejka zgłoszenia musi być przyznana grupami wywołującego, inaczej `not_found_or_denied` bez treści i bez zapisu;
- `add_note`: ten sam resolver przed metodą, potem tylko zapis; przy odmowie nie ma żadnego zapisu;
- `search_tickets`: kolejka z argumentu jest rozwiązywana (istnienie, klasyfikacja) zanim pójdzie
  zapytanie i musi być przyznana grupami wywołującego (`QueryScope.grantedIds()`); wywołujący bez żadnej przyznanej kolejki
  (`nothingToQuery()`) dostaje pusty wynik bez wywołania systemu (zwykle wcześniej odmawia starter: `not_found_or_denied`); trafienia sprawdzane po bieżącej kolejce zgłoszenia; z wyników zostają tylko
  zgłoszenia, które system raportuje w tej kolejce (zgłoszenia przeniesione, a nadal w indeksie, i trafienia
  z innych kolejek odpadają).

Zgłoszenie przeniesione do nieprzyznanej kolejki przestaje być dostępne od następnego wywołania. Między
odczytem a zapisem notatki (dwa wywołania systemu) zostaje okno, w którym zgłoszenie może zmienić kolejkę;
generyczne REST nie daje warunkowego zapisu (STATUS.md). Ponowny odczyt tuż przed zapisem sprawdza granty i politykę
(zgłoszenie przeniesione do kolejki `restricted` nie dostaje notatki).

## Polityka zasobów

Kolejka i zgłoszenie opisane są dla Cedar tak, jak podaje je system (entity-builder.md): zgłoszenie ma rodzica
`ServiceDesk::Queue` z kolejki zwróconej teraz przez system, a klasyfikacja zgłoszenia to klasyfikacja kolejki.
Klasyfikację czyta się z `servicedesk.api.queue.classification-pointer` (napis albo tablica napisów). **Kolejka bez
klasyfikacji jest traktowana jako niezastrzeżona**: to decyzja człowieka, zostawiona świadomie. Klasyfikacja, której
nie da się odczytać (obiekt, liczba, tekst z znakami niewidocznymi, ponad 64 znaki), jest błędem, nie brakiem.
Wyniki wyszukiwania sprawdza `ResultFilter` po `@ResourceRef` w polu `id` trafienia: trafienie, którego zgłoszenie jest
teraz w kolejce spoza grantów albo `restricted`, znika; licznika z systemu nie ma. Koszt: jedno `GET` zgłoszenia na
trafienie i jedno `GET` kolejki na kolejkę wyniku (generyczne REST nie ma pobrania zbiorczego).

Paczka polityk (`MCP_POLICY_*`, `GET /mcp-manifest`) jest wymagana poza `dev`.

### Manifest i rejestracja

`accepted-credential-types` to na stałe `[service-account]` (typ domyślny, pierwszy na liście, trafia do
`context.credentialType` w Cedar). Do rejestru idzie artefakt budowy, nie odpowiedź działającego serwera:
`mcp-app/target/mcp-manifest.json`. Rejestruje go potok CI certyfikatem `mcp-registry-ci-mcp-servicedesk`; `add_note`
(`UPDATE`) czeka w gateway'u na zatwierdzenie administratora. Manifest z `environment: dev` publikuje wyłącznie profil
`devstack` (klucze `dev-` i ta wartość są odrzucane poza trybem dev).

Artefakt powstaje w zwykłym buildzie (`./mvnw package`), także z `-DskipTests`: `ManifestExport` (faza `package`,
`exec-maven-plugin` w `mcp-app/pom.xml`, `manifest.export.skip=false`) czyta konfigurację produkcyjną (`application.yml` bez
profilu) i listę narzędzi z `McpToolsConfiguration`, nie startuje serwera (potrzebowałby magazynów TLS, Redisa i paczki polityk)
i używa tego samego `ManifestGenerator` z tymi samymi kontrolami co serwer przy starcie, więc manifest, który gateway by odrzucił,
zatrzymuje build. `internalUrl` jest informacyjny (z `MCP_INTERNAL_URL`, a gdy go brak, `https://mcp-servicedesk.internal:8443/mcp`).
`ManifestTest` pilnuje, że narzędzia tego pliku są identyczne z `tools/list` działającego serwera (hash definicji obejmuje też
typy poświadczeń, więc w teście porównywane są definicje bez hasha). `ci/run.sh verify` wymaga tego pliku.

### Metryki

Nadmiar zdarzeń `authentication` w audycie liczy `mcp_audit_authentication_suppressed_total` (tagi `component=server`,
`reason`). Alert na jego wzrost jest po stronie panelu operacji; poprzednia nazwa
`mcp_server_audit_authentication_suppressed_total` nie istnieje.

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
| `queue.path`, `queue.id-pointer` | kolejka (istnienie i klasyfikacja dla polityki), placeholder `{queue}`; id kolejki w odpowiedzi | `/api/v1/queues/{queue}`, `/id` |
| `queue.classification-pointer` | klasyfikacja kolejki (napis albo tablica); puste albo brak w odpowiedzi = kolejka niezastrzeżona | `/classification` |
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
    queue: {path: /rest/queues/{queue}, id-pointer: /queueId, classification-pointer: /category}
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
  -> starter: walidacja argumentów względem inputSchema (inaczej C10 invalid_argument + C8)
  -> starter: Cedar `call` na narzędziu, potem resolver `@ResourceRef`: ServicedeskTicketResolver ->
     ServicedeskService (GET zgłoszenia, kontrola id; zgłoszenie z kolejki spoza grantów odpada przed GET kolejki,
     GET kolejki) -> Cedar `update` na zgłoszeniu opisanym przez system (inaczej not_found_or_denied)
  -> ServicedeskTool.addNote: walidacja body
  -> ServicedeskResourceAdapter: CredentialContext.require("servicedesk"), POST notatki
```

## Konfiguracja

Konfiguracja bazowa (`mcp-app/src/main/resources/application.yml`) jest produkcyjna: mTLS, JWKS gateway'a po
TLS z CA wewnętrznym, `jti` w Redisie. Profil `dev` (`application-dev.yml`, testy i uruchomienie lokalne)
wyłącza TLS i mTLS, trzyma `jti` w pamięci i dopuszcza klucze testowe; starter odrzuca te ustawienia poza `dev`.
Odpowiedź systemu jest ograniczona do 1 MB (`servicedesk.backend.max-response-size`), przekierowania 3xx są
błędem, `/actuator/prometheus` nie jest wystawiony.

| Zmienna | Znaczenie | Domyślnie |
| --- | --- | --- |
| `MCP_TLS_KEYSTORE`, `MCP_TLS_TRUSTSTORE`, `MCP_TLS_STORE_PASSWORD` | PKCS12: certyfikat serwera (SAN `mcp-servicedesk.internal`) i CA wewnętrzne; truststore służy też do JWKS | wymagane |
| `MCP_GATEWAY_JWKS_URI` | JWKS wewnętrznego JWT, np. `https://mcp-gateway.internal:8443/.well-known/jwks.json` | wymagane |
| `MCP_GATEWAY_ISSUER` | `iss` C2 | `https://mcp-gateway.internal` |
| `MCP_CONNECT_URL` | strona portalu do połączenia konta, z `{system}`; trafia do `credential_required` | wymagane |
| `MCP_SERVICEDESK_URL` | adres bazowy API service desku (bez query i fragmentu); poza `dev` tylko `https` | wymagane |
| `MCP_SERVICEDESK_ALLOW_PLAIN_HTTP` | `true` dopuszcza `http` poza `dev`, wyłącznie dla mocków `mcp-platform-dev` (poświadczenie idzie wtedy otwartym tekstem; ostrzeżenie w logu) | `false` |
| `MCP_SERVICEDESK_SSL_BUNDLE` | bundle SSL dla HTTPS systemu, np. `mcp-internal-ca`; puste = zaufanie JDK | puste |
| `MCP_POLICY_URL`, `MCP_POLICY_KEY_ID`, `MCP_POLICY_PUBLIC_KEY_FILE`, `MCP_POLICY_STATE_DIR` | podpisana paczka polityk (to samo, co ocenia gateway): adres `latest.json`, id i klucz publiczny Ed25519 (SPKI PEM), katalog stanu instancji (ostatnia przyjęta rewizja); bez paczki w 30 s serwer nie wstaje | wymagane |
| `MCP_INTERNAL_URL` | adres serwera w manifeście (`GET /mcp-manifest`); typy poświadczeń są stałe: `service-account` | wymagane |
| `MCP_REDIS_HOST`, `MCP_REDIS_PORT`, `MCP_REDIS_USER`, `MCP_REDIS_PASSWORD` | Redis dla jednorazowego `jti` (użytkownik ACL `mcp-servicedesk`, klucze `jti:mcp-servicedesk:*`) | `localhost`, `6379`, `mcp-servicedesk`, puste |
| `MCP_PORT`, `MCP_BIND` | port i adres nasłuchu | `8443`, `0.0.0.0` |

Endpoint MCP to `/mcp` (domyślne ustawienie startera, tak jak oczekuje gateway: `internal-url:
https://mcp-servicedesk.internal:8443/mcp`). Bez JWT odpowiada tylko `/actuator/health` (i `liveness`,
`readiness`), ale TLS i tak wymaga certyfikatu klienta z CA wewnętrznego.

Poza profilem `dev` serwer nie wstaje bez `MCP_GATEWAY_JWKS_URI` (tylko `https`), `MCP_CONNECT_URL`,
`MCP_SERVICEDESK_URL` (`https`) i plików TLS. Od startera 3.0.0-alpha-4 wymaga też, żeby `server-id` był na liście
`SystemId` kontraktów; `servicedesk` jest.

`java` w `PATH` może być starsza niż 25 (wtedy `UnsupportedClassVersionError`), dlatego poniżej zawsze
`$JAVA_HOME/bin/java`.

## Uruchomienie lokalne bez środowiska

Profil `dev` (musi być jedynym aktywnym profilem): zwykłe HTTP na 127.0.0.1, `jti` w pamięci, bez Redisa,
certyfikatów i sekretów. Gateway'a nie ma, więc każde żądanie z JWT kończy się 503 (JWKS niedostępny); ten tryb
służy do sprawdzenia, że serwer wstaje i odrzuca ruch bez JWT. Narzędzia sprawdzają testy (`./mvnw verify`).

```bash
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@25/25.0.4.1/libexec/openjdk.jdk/Contents/Home
./mvnw -B verify
SPRING_PROFILES_ACTIVE=dev MCP_PORT=18530 "$JAVA_HOME/bin/java" -jar mcp-app/target/mcp-servicedesk.jar
# w drugim terminalu:
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:18530/actuator/health          # 200
curl -s -o /dev/null -w '%{http_code}\n' -X POST -H 'Content-Type: application/json' \
     -H 'Accept: application/json, text/event-stream' \
     -d '{"jsonrpc":"2.0","id":1,"method":"ping"}' http://127.0.0.1:18530/mcp            # 401
```

Wartości domyślne profilu są w `mcp-app/src/main/resources/application-dev.yml` (backend
`http://localhost:18081`, portal `http://localhost:18090`); każdą można nadpisać zmienną z tabeli wyżej.

Ryzyko: `application-dev.yml` jest w jarze produkcyjnym, a profil `dev` przyjmuje JWT podpisane opublikowanymi
kluczami testowymi kontraktów i nie wymaga certyfikatu klienta. Dlatego `dev` słucha wyłącznie na adresie pętli
zwrotnej: starter odmawia startu z `dev` przy innym `server.address`, a test `DevProfileLoopbackTest` pilnuje
tego w serwerze. Nie ustawiaj `SERVER_ADDRESS` ani `MCP_BIND` przy `dev` i nigdy nie włączaj `dev` na hoście ani
w kontenerze, do którego łączy się gateway: tam działa wyłącznie konfiguracja bazowa z mTLS.

`SPRING_PROFILES_ACTIVE=dev` wolno ustawić tylko w poleceniu z terminala, jak wyżej. Nie trafia do `.env`, szablonów
compose, jednostek systemd ani obrazu: od 3.0.0-alpha-9 starter nie startuje z `dev` w kontenerze, na Kubernetesie
i pod systemd, a `application-dev.yml` nie ustawia już TLS, mTLS ani magazynu `jti` (robi to starter).

## Uruchomienie z mcp-platform-dev (mTLS)

Konfiguracja produkcyjna na hoście, z certyfikatami i Redisem środowiska `mcp-platform-dev` (`make up` już
uruchomione). Środowisko ma certyfikat (`certs/mcp-servicedesk`, SAN `mcp-servicedesk.internal`) i użytkownika
Redisa `mcp-servicedesk`. Mocka service desku tam nie ma, więc `MCP_SERVICEDESK_URL` wskazuje dowolny port, a
wywołania narzędzi kończą się `not_found_or_denied` (awaria odczytu zgłoszenia jest dla konta serwisowego ukryta; przyczyna w logu).

Magazyny PKCS12 i Redis są chronione hasłami z `$MCP_PLATFORM_DEV/.secrets/dev.env`, więc ten wariant wymaga
ich odczytu:

```bash
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@25/25.0.4.1/libexec/openjdk.jdk/Contents/Home
export MCP_PLATFORM_DEV=<workspace>/mcp-platform-dev           # ścieżka bezwzględna
export MCP_TLS_STORE_PASSWORD="$(grep '^TLS_STORE_PASSWORD=' "$MCP_PLATFORM_DEV/.secrets/dev.env" | cut -d= -f2-)"
export MCP_REDIS_PASSWORD="$(grep '^REDIS_MCP_SERVICEDESK_PASSWORD=' "$MCP_PLATFORM_DEV/.secrets/dev.env" | cut -d= -f2-)"
export MCP_TLS_KEYSTORE="$MCP_PLATFORM_DEV/certs/mcp-servicedesk/keystore.p12"
export MCP_TLS_TRUSTSTORE="$MCP_PLATFORM_DEV/certs/ca/truststore.p12"
export MCP_PORT=18530 MCP_BIND=127.0.0.1 MCP_REDIS_PORT=16379
export MCP_SERVICEDESK_URL=http://localhost:18531 MCP_SERVICEDESK_ALLOW_PLAIN_HTTP=true   # bez mocka; poza dev https
export MCP_GATEWAY_JWKS_URI=https://localhost:18445/.well-known/jwks.json
export MCP_CONNECT_URL='http://localhost:18090/connections?system={system}'
"$JAVA_HOME/bin/java" -jar mcp-app/target/mcp-servicedesk.jar
# w drugim terminalu (te same zmienne MCP_PLATFORM_DEV), certyfikat klienta gateway'a:
TLS=(--cacert "$MCP_PLATFORM_DEV/certs/ca/ca.crt" --resolve mcp-servicedesk.internal:18530:127.0.0.1
     --cert "$MCP_PLATFORM_DEV/certs/mcp-gateway/tls.crt" --key "$MCP_PLATFORM_DEV/certs/mcp-gateway/tls.key")
curl -s -o /dev/null -w '%{http_code}\n' "${TLS[@]}" https://mcp-servicedesk.internal:18530/actuator/health   # 200
curl -s -o /dev/null -w '%{http_code}\n' "${TLS[@]}" -X POST -H 'Content-Type: application/json' \
     -d '{"jsonrpc":"2.0","id":1,"method":"ping"}' https://mcp-servicedesk.internal:18530/mcp                     # 401
```

Połączenie bez certyfikatu klienta jest zrywane w handshake'u. Pełne wywołanie narzędzia wymaga gateway'a z
hosta (`mcp-gateway/scripts/run-dev-host.sh`, JWKS na `localhost:18445`) skonfigurowanego na ten serwer.

## CI

Cała logika jest w `ci/run.sh` (`make ci` woła `ci/run.sh verify`); `.gitlab-ci.yml`, `Jenkinsfile` i `.github/workflows/ci.yml`
to cienkie nakładki, więc to samo działa lokalnie, w obrazie `maven:3.9.12-eclipse-temurin-25` i w każdym z trzech systemów.

| Krok | Co robi |
| --- | --- |
| `ci/run.sh verify` (domyślny) | `hygiene`, `mvnw clean verify`, potem kontrola, że uruchomiono co najmniej jeden test (pominięte nie liczą się) i że `mcp-app/target/mcp-manifest.json` istnieje |
| `ci/run.sh hygiene` | cudzysłowy, spacje i wielokropek spoza ASCII oraz ślady narzędzi w źródłach (perl czyta przez `<<>>`) |
| `ci/run.sh image` | `docker build -t mcp-server-servicedesk:m5-<sha>` jaru z `verify`; tylko gałąź `main`, wymaga demona Dockera |

Inny argument kończy się kodem 2. Merge request i pull request nie dostają sekretów ani gniazda Dockera: testy nie
potrzebują kontenerów, a rejestr Maven z artefaktami platformy (`MCP_MAVEN_REPOSITORY_URL`, zwykła zmienna) jest wtedy
czytany anonimowo (`.ci/settings-anonymous.xml`). Dane dostępu (`MCP_MAVEN_REPOSITORY_USER`, `_PASSWORD`; w GitLabie
zmienne Protected i Masked, w Jenkinsie poświadczenie `mcp-maven-registry`) widzi tylko gałąź `main`, na której działa też
krok `image` (runner z gniazdem Dockera). Rejestracja manifestu w gateway'u (`PUT` z certyfikatem
`mcp-registry-ci-mcp-servicedesk`) jest osobnym krokiem pipeline'u serwera.

## Testy

`./mvnw verify` (JDK 25, bez Dockera): 115 testów.

| Klasa | Co sprawdza |
| --- | --- |
| `SearchTicketsToolTest` | zapytanie budowane przez serwer (`GET /api/v1/queues/IT/tickets?q=...&limit=21`), tekst jako zakodowana wartość, wyniki tylko z kolejki, brak pól spoza mapowania (trafienie z innej, także dozwolonej kolejki odpada); `credential_required` (brak i 401); nieznana kolejka -> `not_found_or_denied`; złe argumenty nie docierają do systemu |
| `GetTicketToolTest` | poświadczenie z C3 dociera do systemu; HTML i pola dodatkowe nie wychodzą; brak poświadczenia i 401; 404 = 403; złe `ticketId` (także pole nadmiarowe) nie docierają do systemu; poświadczenie i treść błędu systemu nie trafiają do wyniku ani logu; nieczytelna odpowiedź, przekierowanie, timeout i odpowiedź ponad limit; nazwa argumentu z fałszywą linią audytu daje `invalid_argument`, zdarzenie C8 i żadnej sfałszowanej linii w logu |
| `AddNoteToolTest` | odczyt zgłoszenia, potem POST z notatką wewnętrzną i linią o zleceniodawcy; `UPDATE` bez `readOnlyHint`/`destructiveHint`; brak poświadczenia, 401 na odczycie i na zapisie; 404, 403 i 403 na zapisie dają ten sam błąd; złe argumenty |
| `QueueBoundaryTest` | konto serwisowe: zgłoszenie z innej kolejki, alias, bez kolejki, 403 i nieistniejące dają bajtowo ten sam błąd w `get_ticket` i `add_note`, bez żadnego zapisu; zgłoszenie przeniesione z dozwolonej kolejki przestaje być dostępne (także w wyszukiwaniu z nieaktualnym indeksem), przeniesione do dozwolonej jest dostępne; wyszukiwanie w niedozwolonej kolejce (także `it` zamiast `IT`) nie dociera do systemu; poświadczenie użytkownika jest odrzucane (`internal_error`) przed narzędziem, system nie jest wołany |
| `ResourcePolicyTest` | zgłoszenie w kolejce `restricted`, przeniesione do niej i z kolejki spoza zakresu konta serwisowego = `not_found_or_denied` bajtowo jak nieistniejące (też `add_note`, bez zapisu); `access_denied` bez grupy; wyszukiwanie konta serwisowego tylko w kolejkach przyznanych grupami; kolejka bez klasyfikacji nie jest zastrzeżona; awaria systemu wygląda jak brak zgłoszenia, a przyczyna jest w logu i audycie; liczba wywołań systemu nie zależy od istnienia zgłoszenia spoza grantów |
| `ManifestTest` | `GET /mcp-manifest` przechodzi reguły `mcp-manifest`, definicje równe `tools/list` (`definitionHash`), ryzyko, `resourceRefs`, polskie tytuły, 401 bez JWT serwisowego; manifest z `ManifestExport` (plik z budowy) przechodzi reguły i ma te same narzędzia co `tools/list` |
| `ProductionConfigurationTest` | konfiguracja bazowa: `client-auth: need`, paczka polityk i `state-dir` wymagane, bez kluczy testowych; backend `http` poza `dev` odrzucony (chyba że jawnie dopuszczony); `dev` na 127.0.0.1 |
| `DevProfileLoopbackTest` | `dev` z adresem innym niż pętla zwrotna nie wstaje |
| `ToolDefinitionsTest` | dokładnie trzy narzędzia z ryzykiem; `inputSchema` zamknięte (`additionalProperties: false`), wzorce z kontraktu, limity długości; opisy bez instrukcji |
| `GatewayOnlyAccessTest` | bez JWT 401 (`tools/call`, `ping`, `initialize`), JWT innego serwera, innego narzędzia, wariant serwisowy i przeterminowany -> 401; token z claimem `vs` albo `vsc` (usunięte w kontraktach 5.x) -> 401; bez JWT tylko health |
| `ServicedeskServiceTest` | use case bez Springa: alias, kolejka z trafienia, limit, notatka po ponownym odczycie |
| `ServicedeskToolTest` | walidacja tekstu w narzędziu bez walidacji schematu przed nią (`InvalidToolArgumentException`) |
| `ServicedeskApiTest`, `TicketJsonTest`, `PlainTextTest` | kodowanie URI, treść notatki, walidacja konfiguracji (także nakładające się pola stałe notatki), dwa różne kształty API, HTML -> tekst (zakodowany tag nie wraca jako znacznik) |

`TestPolicyBundle` buduje paczkę testową: przykładowe polityki kontraktów (pełny zestaw, z `credential-type.cedar`) i grupy zakresu kolejek. `StubBackend` (test) to serwer HTTP udający service desk w układzie referencyjnym; w `mcp-platform-dev` nie ma
mocka service desku.

## Dokumentacja

- `docs/jak-napisac-narzedzie.md`: zasady i kolejność kroków przy nowym narzędziu.
- `docs/architecture/overview.md`: architektura i decyzje.
- `docs/security/SECURITY.md`: niezmienniki bezpieczeństwa w tym serwerze.
- `docs/api/tools-catalog.md`: rejestr narzędzi.
- `docs/operations/deployment-vm-systemd.md`, `deploy/`, `Dockerfile`: wdrożenie.
