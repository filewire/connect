# What we built (app + backend work)

## Backend (old Oracle VPS — operational before account loss)

Stack under `/home/ubuntu/docker_data/chat/`, Portainer stack **`matrix`**, Docker network **`matrix-network`**:

- PostgreSQL 16
- Synapse (`matrix-synapse`) server name `matrix.filewire.eu.org`
- Sygnal (`matrix-sygnal`) FCM for `org.filewire.connect`
- LiveKit SFU + MatrixRTC JWT (`rtc.filewire.eu.org`)
- Coturn for TURN
- Cloudflare Tunnel for HTTPS APIs (not for UDP media)
- Firebase project `matrix-chat-0143`

Element Desktop + Connect used this homeserver. Official Play Store Element X uses **Element’s** push, not your Sygnal — **Connect** is what registers `org.filewire.connect` + internal Sygnal URL.

## Android milestones

| Milestone | Status |
|---|---|
| 1 Branding (name, logo, icons, Filewire HS) | Done |
| 2 Privacy hub / skip login confirm / FCM gplay | Partially (app lock exists upstream — **do not casually edit lockscreen**) |
| 3 Messaging | Upstream Element X; not a custom rewrite |
| 4 Calls | In progress — see below |
| 5 Bottom nav Chats/Calls/Contacts/Settings | Not done |

### Call phases (internal)

| ID | Scope | Status |
|---|---|---|
| A | Timeout 45s, errors, Retry | Done |
| B | Ringback / busy UX | Not done |
| C | GSM audio focus | Not done |
| D | Hang-up, WebView, START vs JOIN | Iterated; `58b703e9c2` is the JOIN + expired-ring fix |
| E2 | Audio playback capture | Not done |
| UI | Bottom nav | Not done |

## Important git commits (newest first, as of this doc)

| SHA | Note |
|---|---|
| `58b703e9c2` | JOIN when `hasRoomCall`; no ring if expired RING and call ended; drop(1) removed on ring observer |
| `566b9c3772` | Duplicate About/Sign out test name |
| `0fa476b487` | JOIN only when answering incoming; late FCM still ring |
| `59c9a159c5` | JOIN if Ringing; hang-up UI immediate |
| `f5f78c6fdd` | Always START (broke answering live calls) |
| `a00d30f9be` | Sygnal URL `http://matrix-sygnal:5000/...` |
| `af27779613` | Package `org.filewire.connect` + Firebase/Sygnal ids |
| `842ee19bd5` | Milestone 1 branding |

## Call behaviour still to verify after JOIN fix

- Phone → laptop: Please wait if START hits a live/zombie session
- Laptop → phone locked: FCM + full-screen intent
- App open: Join button vs ring
- Ghost ring after remote hang-up

Tests may still need `MatrixClientProvider` on `DefaultElementCallEntryPointTest`.

## Constraints for any AI

- No `git config --global`
- No force-push
- No secrets in git
- User: bodduna
