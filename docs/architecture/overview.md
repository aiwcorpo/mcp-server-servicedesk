# Architektura

## Miejsce w platformie

```
klient AI --OIDC--> MCP gateway --mTLS + JWT C2 + poświadczenie C3--> mcp-servicedesk --API--> system servicedesk
                       |  ^
                       |  +-- credential broker (poświadczenie: konto serwisowe albo token użytkownika)
                       +-- polityki (Cedar), potwierdzenia, audyt
```

Gateway uwierzytelnia użytkownika, decyduje o dostępie do narzędzia, pobiera z brokera poświadczenie
do systemu (dla service desku domyślnie konto serwisowe, D10) i wysyła do serwera nowy wewnętrzny JWT na każde żądanie HTTP. Serwer sprawdza, że
żądanie przyszło od gateway'a (certyfikat klienta CN `mcp-gateway`, JWT z `aud` = `mcp-servicedesk`, `tool` ==
nazwa wywoływanego narzędzia, jednorazowy `jti`), i woła system z tym poświadczeniem; dostęp do kolejek wynika wyłącznie z grantów w paczce polityk. Serwer nie zna tokenu
IdP użytkownika i nie przechowuje żadnego sekretu.

## Heksagon w modułach

```
            mcp-tools (@Tool)                      mcp-integration (REST do systemu)
                  |                                         |
                  v                                         v implementuje
           mcp-application (przypadki użycia) ---> mcp-core (domena, porty in/out)
                              mcp-app: bootstrap, wiring, jar, testy end-to-end
             wspólne: mcp-platform-starter (JWT C2, C3, audyt C8, błędy C10, transport MCP)
```

`mcp-core` nie zależy od Springa ani MCP, więc reguły domeny testuje się bez transportu. Kod przekrojowy jest
w starterze; serwer go nie powiela.

## Transport

Streamable HTTP (Spring AI 2.0, MCP SDK 2.0, tryb SYNC), endpoint `/mcp`, wersje protokołu 2025-03-26 ..
2025-11-25, sesje `Mcp-Session-Id`. Gateway trzyma osobną sesję na użytkownika (`sub`), więc serwer nie wiąże
sesji z użytkownikiem sam. `CallerContext` i `CredentialContext` są per wątek żądania, dlatego starter odrzuca
tryb ASYNC.

## Decyzje

- mTLS kończy sam serwer (`server.ssl.client-auth: need`), bez nginx przed nim: certyfikat klienta musi dotrzeć
  do aplikacji, a gateway sprawdza SAN `mcp-servicedesk.internal` serwera.
- Konfiguracja bazowa jest produkcyjna; profil `dev` istnieje dla testów i wyłącza tylko TLS.
- Jeden system na serwer: `server-id` = `mcp-<system>`, `CredentialContext.require` innego systemu to błąd
  programisty.
- Adapter nie podąża za przekierowaniami i nie czyta treści błędów systemu.
- Ponawianie i circuit breaker w adapterze: M2 template'u (teraz tylko timeouty).
