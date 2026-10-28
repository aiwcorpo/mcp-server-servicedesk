# Katalog narzędzi

Narzędzia to powierzchnia publiczna serwera. Każda zmiana nazwy, opisu, schematu albo ryzyka zmienia
`definitionHash` (C6) i wymaga ponownego zatwierdzenia w gateway'u, więc przechodzi przez code review razem z
tą tabelą.

| Narzędzie (klient widzi `servicedesk__<nazwa>`) | Tytuł | Ryzyko | Akcja (C6) | Argumenty | Uprawnienie (C9) | Poświadczenie |
| --- | --- | --- | --- | --- | --- | --- |
| `search_tickets` | Szukaj zgłoszeń w kolejce | `READ_ONLY` | `read` | `queue` (obowiązkowa, `ServiceDeskQueueId`), `text` (1-200) | `mcp-servicedesk-read` | `service-account` albo `user-secret` z C3 |
| `get_ticket` | Pobierz zgłoszenie | `READ_ONLY` | `read` | `ticketId` (`ServiceDeskTicketId`) | `mcp-servicedesk-read` | jw. |
| `add_note` | Dodaj notatkę do zgłoszenia | `UPDATE` | `update` | `ticketId` (`ServiceDeskTicketId`), `body` (1-10000) | `mcp-servicedesk-write` | jw. |

Wzorce z `mcp-contracts` 3.1.0 (czytane z kontraktu, nie kopiowane): `ServiceDeskQueueId` i `ServiceDeskTicketId` = `^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$`.
Zasoby C7 (`@ResourceRef`): `queue` -> `ServiceDesk::Queue{id}`, `ticketId` -> `ServiceDesk::Ticket`
(rodzic: kolejka, którą zwraca system; atrybut `classification` kolejki jest opcjonalny). Opisy narzędzi (dokładny tekst, wchodzi do `definitionHash`) są w
`ServicedeskTool`, limity wejścia w `@ArgumentLimits` (wzorce przez `contractPattern`).

Tekst z service desku (`subject`, `description`) ma w wyniku kształt `UntrustedText` (CC-072):
`{kind: "untrusted-data", format: "text/plain", text, truncated}`. `add_note` przy koncie serwisowym dokleja na
początku treści linię o zleceniodawcy (README, "Autor notatki przy koncie serwisowym").

Konwencje: nazwy `snake_case`, czasownik i obiekt, bez prefiksu systemu (dokłada go gateway); opis mówi, co
narzędzie zwraca, bez instrukcji dla modelu.
