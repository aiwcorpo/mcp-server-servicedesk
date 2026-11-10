# Install mcp-servicedesk

The service desk MCP server as one container. It runs on any host that the platform can reach; the platform (gateway, broker,
portal) is installed separately.

## Requirements

- Docker Engine with Compose v2.20 or newer, 1 GiB of memory.
- The domain of the platform, from its operator. This host must reach `https://<platform>` on port 443.
- The platform must reach this host on port 8443.
- The address of the service desk API (`https`).
- A `servicedesk.api` file for your product, see the README. Without it the server uses the reference layout.
- The key of the platform's service account for the service desk, in OpenBao at `mcp/service-accounts/servicedesk`. The broker reads
  it; this server never holds it.

## Install

1. Fill in the three empty values in `mcp-servicedesk.env`:

    ```bash
    MCP_GATEWAY_JWKS_URI=https://<platform>/.well-known/mcp-jwks.json
    MCP_CONNECT_URL=https://<platform>/connections?system={system}
    MCP_SERVICEDESK_URL=https://<servicedesk>
    ```

2. If your service desk is not the reference layout, put your `servicedesk.api` file in `certs/` (any file in that directory is
   mounted read-only at `/certs`) and add one line to `mcp-servicedesk.env`:

    ```bash
    SPRING_CONFIG_ADDITIONAL_LOCATION=file:/certs/api.yml
    ```

3. If the platform certificate is signed by a company CA, add that CA to a trust store in `certs/`:

    ```bash
    keytool -importcert -noprompt -alias company-ca -file company-ca.crt \
      -keystore certs/truststore.p12 -storetype PKCS12 -storepass changeit
    chmod 644 certs/truststore.p12
    ```

    Then uncomment the `JAVA_TOOL_OPTIONS` line in `mcp-servicedesk.env`. When the service desk uses a CA that the JVM does not
    know, import that CA into the same file.

4. Start the server:

    ```bash
    docker compose up -d
    ```

5. Read the certificate fingerprint:

    ```bash
    docker compose logs mcp-servicedesk | grep event=tls_certificate
    ```

    Expected: `event=tls_certificate source=generated fingerprint=sha256:...`

6. Send the platform operator the address `https://<this host>:8443/mcp` and the fingerprint.

## Check that it works

```bash
curl -fsSk https://localhost:8443/actuator/health
```

Expected: `{"status":"UP"...}`. Every other path answers 401 without a token from the gateway. An invalid `servicedesk.api` stops
the server at start, and the log says which key is wrong.

## Add the server to the platform

The platform operator does this in the portal, Administration, with a fresh login and OTP:

1. Systems: add `servicedesk`, adapter `generic-rest`, address `https://<servicedesk>`, credential type `service-account`, scheme
   `ApiKey`. Users connect nothing: the broker uses the service account.
2. Servers, Add server: `https://<this host>:8443/mcp`.
3. Approvals: compare the fingerprint with the one from step 5 and approve the connection, then the policies, then every tool
   (the read-only ones too).
4. In Keycloak, create the groups `mcp-servicedesk-read` and `mcp-servicedesk-write` and add the users.
5. Scope mapping: a group such as `mcp-servicedesk-queue-IT` -> `ServiceDesk::Queue/IT`. A user with no queue group gets nothing,
   because the service account sees every queue.

## Common problems

| Symptom | Cause | Fix |
| --- | --- | --- |
| Server stops at start: cannot fetch the key set | The host does not resolve `<platform>` or does not trust its certificate | Check DNS; do step 3 |
| Portal, Add server: `tls_handshake_failed` or `manifest_unreachable` | Port 8443 closed between the platform and this host | Open it to the platform host |
| Tool call: `upstream_unavailable`, log has `SSLHandshakeException` | The trust store is missing the service desk CA, or `JAVA_TOOL_OPTIONS` is commented out | Import the CA, then `docker compose up -d --force-recreate` |
| Tool call: `not_found_or_denied` for a ticket that exists | The user has no group mapped to the queue of the ticket, or the pointers in `servicedesk.api` do not match the response | Check the scope mapping; compare `queue.id-pointer` and `fields.queue` with a real response |
| Server status `certificate_changed` | The state volume was recreated and the server generated a new certificate | Approve the new fingerprint in the portal |

The state volume keeps the certificate between restarts. `docker compose down -v` deletes it.

## Your own certificate

Put a PEM pair or a PKCS12 keystore in `certs/` and set `MCP_TLS_CERT` and `MCP_TLS_KEY`, or `MCP_TLS_KEYSTORE` and
`MCP_TLS_STORE_PASSWORD`, in `mcp-servicedesk.env`. A certificate from a CA the gateway trusts, for the host name in the address, is
trusted without pinning, and a renewal needs no new approval.

## Several instances

Instances behind one address need the same values in `mcp-servicedesk.env`:

| Setting | Why |
| --- | --- |
| `MCP_REDIS_HOST` and the other `MCP_REDIS_*` | one-time token ids shared between instances |
| `MCP_PAGING_CURSOR_KEY` (`openssl rand -base64 32`) | a cursor from one instance works on another |
| `MCP_TLS_CERT` and `MCP_TLS_KEY` | the gateway sees one fingerprint |

## Closed network

Copy the image to your registry and set it in a `.env` file next to `compose.yaml`:

```bash
MCP_REGISTRY=artifactory.corp.test/mcp
```

## Upgrade from 1.x

Remove `MCP_GATEWAY_POLICY_DATA_URL`, `MCP_GATEWAY_TLS_NAME`, `MCP_INTERNAL_URL`, `MCP_TLS_TRUSTSTORE` and the Redis service of the
old compose file, set `MCP_GATEWAY_JWKS_URI`, and start. The operator approves the connection and the policies again; the server
refuses to start while an old setting is left.

## Virtual machine without Docker

`systemd/` has a unit and its environment file for the jar (`mcp-app/target/mcp-servicedesk.jar` after `./mvnw verify`):

```bash
sudo useradd --system --no-create-home --shell /usr/sbin/nologin mcp
sudo install -d -o root -g mcp -m 0750 /opt/mcp-servicedesk /etc/mcp-servicedesk
sudo install -o root -g mcp -m 0640 mcp-servicedesk.jar /opt/mcp-servicedesk/mcp-servicedesk.jar
sudo install -o root -g mcp -m 0640 systemd/mcp-servicedesk.env.template /etc/mcp-servicedesk/mcp-servicedesk.env
sudo install -m 0644 systemd/mcp-servicedesk.service /etc/systemd/system/
```

Fill in `/etc/mcp-servicedesk/mcp-servicedesk.env` as in steps 1 and 2 (put the `servicedesk.api` file next to it), then:

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now mcp-servicedesk
journalctl -u mcp-servicedesk | grep event=tls_certificate
```
