# Connect — system overview

## Product

**Connect** is a branded fork of **Element X Android** (Kotlin, Jetpack Compose, `matrix-rust-sdk`). Users should see a WhatsApp/Signal-like app; Matrix is only the backend.

| Item | Value |
|---|---|
| Display name | Connect |
| GitHub | https://github.com/filewire/connect |
| Working branch | `develop` |
| Release `applicationId` | `org.filewire.connect` |
| Debug `applicationId` | `org.filewire.connect.debug` |
| Website / legal | https://filewire.eu.org , `/privacy`, `/terms` |
| Default homeserver in app | `https://matrix.filewire.eu.org` |
| Calls (LiveKit / MatrixRTC) | `https://rtc.filewire.eu.org` |
| Local Windows checkout used in development | `E:\Chat V0.1\connect-git` |
| Unused local snapshot | `E:\Chat V0.1\connect-develop` (no `.git` — not source of truth) |

`AuthenticationConfig.MATRIX_ORG_URL` is **still named** `MATRIX_ORG_URL` but the **value is Filewire**, not matrix.org.

## What still exists without the VPS

**In Git (this repo):** Android source, branding, Firebase **client** IDs, Sygnal **app_id**, homeserver URL, push gateway URL used by the app.

**Not in Git (lost with Oracle unless you copied them):** Postgres data, Synapse `homeserver.yaml` + signing keys, user accounts (`@test1`, `@rakesh`, …), media store, LiveKit keys on the server, Coturn secrets, Cloudflare tunnel token on that VM, TLS certs if any were local.

**On the laptop (do not put in Git):** `E:\Chat V0.1\API Keys.txt` (LiveKit key pair), `E:\Chat V0.1\firebase.json` (Firebase **service account** private key for Sygnal). Copy those files to a password manager **yourself**. This repo documents **how** to use them, not the secret values.

## Lost VPS (historical — do not reuse IP)

- Hostname used in chats: `vps-02`
- Data root: `/home/ubuntu/docker_data/chat/`
- Docker network: `matrix-network`
- Public IPv4 **at the time** (Oracle): `150.230.141.228` — **dead with the old instance**. New server = new IP. Update LiveKit `node_ip`, Coturn `external-ip`, Oracle/security lists, Cloudflare.

## Architecture (what you must rebuild)

```
Android Connect / Element Desktop
        │ HTTPS (Cloudflare Tunnel)
        ▼
matrix.filewire.eu.org  →  Synapse :8008
        │
        │ Docker network matrix-network
        ├─► Postgres 16
        └─► Sygnal :5000  (FCM; app registers http://matrix-sygnal:5000/_matrix/push/v1/notify)

        │ HTTPS (Cloudflare Tunnel for HTTP API)
        ▼
rtc.filewire.eu.org
        ├─ /livekit/jwt  →  lk-jwt-service (Matrix OpenID → LiveKit JWT)
        └─ LiveKit SFU   →  UDP/TCP media (NOT through Cloudflare Tunnel)

        └─ Coturn (STUN/TURN) on public UDP — required behind NAT
```

**Calls are not native WebRTC in Connect.** The app loads **embedded Element Call** in a WebView. Media still goes to **your** LiveKit.

## Hostnames (Cloudflare DNS + Tunnel)

| Hostname | Service |
|---|---|
| `matrix.filewire.eu.org` | Synapse client + federation API |
| `rtc.filewire.eu.org` | LiveKit JWT (+ LiveKit HTTP API as you configured) |
| `filewire.eu.org` | Marketing / privacy / terms (static site if you had one) |

Optional historic names (`element.filewire.eu.org`) were discussed; in-app calling does **not** require a public Element Call website.

## Runtime on the old VPS

- Ubuntu + **Docker** + **Portainer** (stacks)
- **cloudflared** (Cloudflare Tunnel) — HTTP/S for Matrix and RTC JWT
- Oracle Cloud **Always Free** ARM-class box (was ~4 ARM cores / 24 GB RAM / 200 GB in early planning notes)

## Test users (accounts were on Synapse — recreate after restore)

| Client | Typical MXID |
|---|---|
| Phone Connect | `@test1:matrix.filewire.eu.org` |
| Laptop Element Desktop | `@rakesh:matrix.filewire.eu.org` |

## Git author for this repo (env only — do not run `git config --global`)

```text
filewire
312208060+filewire@users.noreply.github.com
```
