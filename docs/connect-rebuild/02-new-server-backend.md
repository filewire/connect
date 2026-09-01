# New server — backend, step by step

Rebuild on **any** VPS (Oracle, Hetzner, etc.). Paths below match the old machine so you can copy-paste; change `/home/ubuntu` if your user differs.

**You cannot restore Matrix users/rooms without a Postgres + Synapse data backup.** This guide is a **greenfield** rebuild: new database, new accounts, same hostnames and same Android app.

Official LiveKit/Element Call details change; after Synapse is up, follow current:

https://github.com/element-hq/element-call/blob/livekit/docs/self_hosting.md

---

## 0. Prerequisites

1. Domain **filewire.eu.org** still in Cloudflare.
2. New VPS: Docker + Portainer (as before).
3. New **public IPv4** (and IPv6 if you use it). Note it: `YOUR_NEW_PUBLIC_IP`.
4. Open **Oracle/security group / ufw** for:
   - SSH
   - LiveKit UDP/TCP as in current LiveKit docs (typically UDP 50000–60000 or the range in `livekit.yaml`)
   - Coturn UDP/TCP (commonly 3478, 5349, and relay range)
   - **Do not** rely on Cloudflare Tunnel for WebRTC media.

5. Cloudflare Tunnel: create a **new** tunnel (old VM token is gone). Public hostnames:
   - `matrix.filewire.eu.org` → `http://matrix-synapse:8008` **or** `http://HOST_IP:8008` depending on whether cloudflared is on `matrix-network`
   - `rtc.filewire.eu.org` → JWT service (and LiveKit HTTP if you proxy it)

If cloudflared is a **container**, attach it to `matrix-network` and use **container names**, not `localhost`.

---

## 1. Directories

```bash
sudo mkdir -p /home/ubuntu/docker_data/chat/{synapse/data,postgres/data,sygnal,livekit,matrixrtc,coturn}
cd /home/ubuntu/docker_data/chat
```

Synapse UID:

```bash
sudo chown -R 991:991 /home/ubuntu/docker_data/chat/synapse/data
```

---

## 2. Generate Synapse config

```bash
docker run --rm -it \
  -v /home/ubuntu/docker_data/chat/synapse/data:/data \
  -e SYNAPSE_SERVER_NAME=matrix.filewire.eu.org \
  -e SYNAPSE_REPORT_STATS=no \
  matrixdotorg/synapse:latest generate
```

Expect files including `homeserver.yaml`, `matrix.filewire.eu.org.signing.key`, log config.

---

## 3. Postgres + Synapse (Portainer stack)

Stack name used before: **`matrix`**.

**homeserver.yaml — database** (password must match compose):

```yaml
database:
  name: psycopg2
  args:
    user: synapse
    password: CHANGE_THIS_DB_PASSWORD
    database: synapse
    host: matrix-postgres
    cp_min: 5
    cp_max: 10
```

**listeners:** keep `client` and `federation`. Set `x_forwarded: true` for Cloudflare.

**Compose skeleton** (fix YAML: every service under `services:`):

```yaml
services:
  postgres:
    image: postgres:16-alpine
    container_name: matrix-postgres
    restart: unless-stopped
    environment:
      POSTGRES_USER: synapse
      POSTGRES_PASSWORD: CHANGE_THIS_DB_PASSWORD
      POSTGRES_DB: synapse
      POSTGRES_INITDB_ARGS: "--encoding=UTF8 --locale=C"
    volumes:
      - /home/ubuntu/docker_data/chat/postgres/data:/var/lib/postgresql/data
    networks:
      - matrix-network

  synapse:
    image: matrixdotorg/synapse:latest
    container_name: matrix-synapse
    restart: unless-stopped
    depends_on:
      - postgres
    volumes:
      - /home/ubuntu/docker_data/chat/synapse/data:/data
    ports:
      - "127.0.0.1:8008:8008"
    networks:
      - matrix-network

networks:
  matrix-network:
    name: matrix-network
```

Deploy, then:

```bash
curl http://127.0.0.1:8008/_matrix/client/versions
curl https://matrix.filewire.eu.org/_matrix/client/versions
```

Create users with Synapse `register_new_matrix_user` (or disable registration and create via admin).

---

## 4. MatrixRTC on Synapse

Current Synapse style (prefer this + official docs):

```yaml
matrix_rtc:
  transports:
    - type: livekit
      livekit_service_url: https://rtc.filewire.eu.org/livekit/jwt
```

On the **old** server, `homeserver.yaml` also had experimental:

```yaml
  org.matrix.msc4143.rtc_foci:
    - type: livekit
      livekit_service_url: "https://rtc.filewire.eu.org"
```

If Element Call cannot get a JWT, try the `/livekit/jwt` URL first (matches Element Call self-hosting). Restart Synapse after edits.

**OpenID / federation listener** must be reachable by **lk-jwt-service** (usually same Docker network → `http://matrix-synapse:8008`).

Optional (call leftover membership / delayed leave — product still warns testers not to tap old Join):

- Research Synapse `max_event_delay_duration` / MSC4140 delayed events on **current** Synapse docs if Join timers linger after hang-up.

---

## 5. LiveKit + lk-jwt-service

### Keys

Generate **new** keys (old `API Keys.txt` is useless if LiveKit data is gone; even if you still have the file, a new cluster should use **new** secrets):

```bash
echo "LIVEKIT_KEY=matrix$(openssl rand -hex 8)"
echo "LIVEKIT_SECRET=$(openssl rand -hex 32)"
```

Put the **same** pair in:

- `livekit.yaml` (`keys:`)
- lk-jwt-service environment (`LIVEKIT_KEY` / `LIVEKIT_SECRET`)

### livekit.yaml (starting point)

Put `YOUR_NEW_PUBLIC_IP` in `rtc.node_ip` (or current LiveKit equivalent). Old IP was `150.230.141.228`.

```yaml
port: 7880
bind_addresses:
  - "0.0.0.0"

rtc:
  tcp_port: 7881
  port_range_start: 50000
  port_range_end: 50100
  use_external_ip: true
  # node_ip: YOUR_NEW_PUBLIC_IP   # if auto-detect fails

keys:
  LIVEKIT_KEY_PLACEHOLDER: LIVEKIT_SECRET_PLACEHOLDER
```

Adjust UDP range to match firewall. Expose LiveKit ports on the **host**, not only 127.0.0.1.

### JWT service

Use current `element-hq/lk-jwt-service` image. Typical env:

- `LIVEKIT_URL` — `wss://rtc.filewire.eu.org` or `ws://matrix-livekit:7880` as docs require
- `LIVEKIT_KEY` / `LIVEKIT_SECRET` — same as LiveKit
- Synapse URL for OpenID: `http://matrix-synapse:8008`

Cloudflare hostname `rtc.filewire.eu.org`:

- `/livekit/jwt` → JWT container
- LiveKit SFU HTTP if needed

**UDP media** goes to `YOUR_NEW_PUBLIC_IP`, not the tunnel.

---

## 6. Coturn

TURN is required for many phone NATs. Official: https://element-hq.github.io/synapse/latest/turn-howto.html

Also wire LiveKit to use TURN if Element Call docs say so for your version.

Open UDP/TCP on the **new** public IP. Cloudflare Tunnel **cannot** replace this.

---

## 7. Sygnal + Firebase (Android push)

### Files on the host

| Host path | Inside container |
|---|---|
| `/home/ubuntu/docker_data/chat/sygnal/sygnal.yaml` | `/etc/sygnal/sygnal.yaml` |
| `/home/ubuntu/docker_data/chat/sygnal/firebase.json` | `/etc/sygnal/firebase.json` |

Copy the Firebase **service account** JSON from your laptop backup (`E:\Chat V0.1\firebase.json`) onto the VPS. **Do not commit it to GitHub.**

### sygnal.yaml (known-working for Connect)

Sygnal **`apps:` keys must match** `PushConfig.PUSHER_APP_ID` = `org.filewire.connect`.

```yaml
http:
  port: 5000
  bind_addresses: ['0.0.0.0']

apps:
  org.filewire.connect:
    type: gcm
    api_version: v1
    project_id: matrix-chat-0143
    service_account_file: /etc/sygnal/firebase.json

  org.filewire.connect.debug:
    type: gcm
    api_version: v1
    project_id: matrix-chat-0143
    service_account_file: /etc/sygnal/firebase.json
```

If the image ignores `--config`, use:

```yaml
  sygnal:
    image: matrixdotorg/sygnal:latest
    container_name: matrix-sygnal
    restart: unless-stopped
    environment:
      - SYGNAL_CONF=/etc/sygnal/sygnal.yaml
    volumes:
      - /home/ubuntu/docker_data/chat/sygnal/sygnal.yaml:/etc/sygnal/sygnal.yaml:ro
      - /home/ubuntu/docker_data/chat/sygnal/firebase.json:/etc/sygnal/firebase.json:ro
    networks:
      - matrix-network
```

Logs should show config path `/etc/sygnal/sygnal.yaml`, not `/sygnal.yaml`.

### How Connect talks to Sygnal

In the **app**:

```text
PushConfig.FCM_PUSHER_HTTP_URL = http://matrix-sygnal:5000/_matrix/push/v1/notify
```

Synapse (on `matrix-network`) calls that URL. The phone does **not** need a public Sygnal URL for real FCM.

**Push loopback** in Settings → Troubleshoot may still **404** if `https://matrix.filewire.eu.org/_matrix/push/v1/notify` is not proxied. Optional nginx:

```nginx
location /_matrix/push {
    proxy_pass http://127.0.0.1:5000;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $remote_addr;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

That is **nice for tests**, not required for Synapse→Sygnal on Docker.

### Firebase project (already used by the Android app)

| Field | Value |
|---|---|
| Project id | `matrix-chat-0143` |
| Project number / GCM sender | `247291343746` |
| Android release App ID | `1:247291343746:android:c8165338a10fd4f473559b` |
| Android debug App ID | `1:247291343746:android:9bb275e8eb91d66473559b` |
| Packages | `org.filewire.connect`, `org.filewire.connect.debug` |

Register those packages in Firebase Console if they were deleted. Download a **new** service account if the old key was leaked or lost.

`google-services.json` on the laptop may still list package `org.amor.chat` — that is an **old** app. Connect uses IDs in `BuildTimeConfig.kt` + `libraries/pushproviders/firebase/.../firebase.xml`.

---

## 8. Smoke tests

1. `https://matrix.filewire.eu.org/_matrix/client/versions`
2. Login Connect (CI **gplay debug** APK) + Element Desktop to the same homeserver.
3. Send a message both ways; confirm Sygnal logs on new message while app is backgrounded.
4. Start a call; LiveKit logs should show **two** participants (not only Electron).
5. Settings → Privacy → Notifications → Troubleshoot → Firebase token.

## 9. What we cannot recover from Git

- Old encrypted rooms, media, devices
- Old Synapse signing key (federation identity is a **new** server unless you restore the key)
- Cloudflare tunnel credential from the dead VM

If Oracle restores the **disk**, copy `/home/ubuntu/docker_data/chat/` first, then this guide is only for networking/IP/DNS updates.
