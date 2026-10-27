# Wdrożenie na VM (systemd)

Serwer działa jako usługa systemd, bez kontenera, i sam kończy mTLS na porcie 8443. Gateway łączy się z nim
pod nazwą `mcp-servicedesk.internal` (SAN certyfikatu serwera).

## Wymagania

- JRE 25 w `/usr/bin/java`, jar `mcp-app/target/mcp-servicedesk.jar` (`./mvnw verify`).
- Certyfikat serwera (SAN `mcp-servicedesk.internal`, CA wewnętrzne) i truststore z CA wewnętrznym, oba PKCS12.
- Redis z użytkownikiem ACL `mcp-servicedesk` (klucze `jti:mcp-servicedesk:*`, komenda `SET`).
- Dostęp sieciowy: od gateway'a do 8443, z serwera do JWKS gateway'a, Redisa i API systemu.

## Układ na hoście

```
/opt/mcp-servicedesk/mcp-servicedesk.jar         0640 root:mcp
/etc/mcp-servicedesk/mcp-servicedesk.env         0640 root:mcp  (z deploy/systemd/mcp-servicedesk.env.template)
/etc/mcp-servicedesk/tls/keystore.p12        0640 root:mcp
/etc/mcp-servicedesk/tls/truststore.p12      0640 root:mcp
```

Logi (aplikacja i audyt C8) idą na stdout, czyli do journald; audyt to linie JSON z loggera
`pl.aiwcorpo.mcp.audit` do przekazania do SIEM.

## Kroki

1. `deploy/scripts/install.sh mcp-app/target/mcp-servicedesk.jar` (konto `mcp`, katalogi, unit).
2. Plik środowiska z szablonu, keystore i truststore do `/etc/mcp-servicedesk/tls`.
3. `systemctl enable --now mcp-servicedesk`, potem `journalctl -u mcp-servicedesk -f`.
4. `deploy/scripts/healthcheck.sh https://mcp-servicedesk.internal:8443 <ca.crt> <cert klienta> <klucz>`; serwer
   wymaga certyfikatu klienta także dla health.

Aktualizacja: `deploy/scripts/update.sh <nowy.jar>` (poprzedni jar zostaje jako `.bak.<data>`). Wycofanie:
przywrócić kopię i `systemctl restart mcp-servicedesk`.

## Typowe problemy

- Brak startu z `Invalid MCP platform configuration`: brakuje `MCP_GATEWAY_JWKS_URI` albo `MCP_CONNECT_URL`.
- 503 na każde wywołanie: Redis niedostępny albo użytkownik ACL bez prawa do `jti:mcp-servicedesk:*`, albo JWKS
  gateway'a niedostępny dłużej niż `jwks-max-stale`.
- 401 mimo działającego gateway'a: inny `iss`/`aud`, zegar przesunięty o ponad 30 s, gateway bez certyfikatu
  CN `mcp-gateway`; powód jest w logu (`event=internal_jwt_rejected reason=...`).
