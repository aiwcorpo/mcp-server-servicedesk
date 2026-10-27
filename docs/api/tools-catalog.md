# Katalog narzędzi

Narzędzia to powierzchnia publiczna serwera. Każda zmiana nazwy, opisu, schematu albo ryzyka zmienia
`definitionHash` (C6) i wymaga ponownego zatwierdzenia w gateway'u, więc przechodzi przez code review razem z
tą tabelą.

| Narzędzie (klient widzi `servicedesk__<nazwa>`) | Ryzyko | Akcja (C6) | Argumenty | Uprawnienie (C9) | Poświadczenie |
| --- | --- | --- | --- | --- | --- |
| `search_tickets` | `READ_ONLY` | `read` | `queue` (obowiązkowa, `ServiceDeskQueueId`), `text` (1-200) | `mcp-servicedesk-read` | `service-account` albo `user-secret` z C3 |
| `get_ticket` | `READ_ONLY` | `read` | `ticketId` (`ServiceDeskTicketId`) | `mcp-servicedesk-read` | jw. |
| `add_note` | `UPDATE` | `update` | `ticketId` (`ServiceDeskTicketId`), `body` (1-10000) | `mcp-servicedesk-write` | jw. |

Wzorce z `mcp-contracts` 2.0.2: `ServiceDeskQueueId` i `ServiceDeskTicketId` = `^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$`.
Zasoby C7 (do `@ResourceRef` w M2/M3): `queue` -> `ServiceDesk::Queue{id}`, `ticketId` -> `ServiceDesk::Ticket`
(rodzic: kolejka, którą zwraca system). Opisy narzędzi (dokładny tekst, wchodzi do `definitionHash`) są w
`ServicedeskTool`, schematy wejścia w `ServicedeskToolSchemas`.

Konwencje: nazwy `snake_case`, czasownik i obiekt, bez prefiksu systemu (dokłada go gateway); opis mówi, co
narzędzie zwraca, bez instrukcji dla modelu.
