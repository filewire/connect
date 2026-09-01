# Connect Android app (frontend)

Laptop is weak (i3 / 8 GB). **Prefer GitHub Actions APK**, not local Gradle.

## Clone

```text
https://github.com/filewire/connect
branch: develop
```

Open **`connect-git`**, not `connect-develop`.

## Conventions

See root `AGENTS.md`:

- Timber only (no `android.util.Log`)
- Never log message bodies / passwords
- New English strings → `temporary.xml` (never edit `localazy.xml`)
- Do not change `features/lockscreen/` unless the user asks
- Compound design system, Metro DI, Appyx + Molecule
- Sentence-style commits; no force-push to `develop`

## Branding (Milestone 1 — done)

| Piece | Location |
|---|---|
| App name | `BuildTimeConfig.APPLICATION_NAME = "Connect"` |
| Package | `org.filewire.connect` |
| Logo source | `branding/logo.png` |
| Launcher icons | `appicon/element/...` — `tools/branding/generate_icons.ps1` |
| Welcome / notification icons | designsystem drawables (`element_logo.png` replaced with Connect) |
| Privacy hub | Settings → Connect privacy (`features/preferences/.../connectprivacy`) |
| Login | Skip extra confirmation; default HS Filewire |

## Config files (in repo)

| File | Role |
|---|---|
| `appconfig/.../AuthenticationConfig.kt` | `MATRIX_ORG_URL = https://matrix.filewire.eu.org` |
| `appconfig/.../PushConfig.kt` | `PUSHER_APP_ID`, Docker-internal Sygnal URL |
| `appconfig/.../ElementCallConfig.kt` | Ring 90s, widget timeout 45s, hang-up grace 2s |
| `plugins/.../BuildTimeConfig.kt` | applicationId, Firebase App IDs, site URLs |
| `libraries/pushproviders/firebase/.../firebase.xml` | Firebase project `matrix-chat-0143` |

If the **homeserver hostname** changes, update `AuthenticationConfig` and login tests that expect title `matrix.filewire.eu.org`.

If Sygnal is **not** named `matrix-sygnal` on the Docker network, change `PushConfig.FCM_PUSHER_HTTP_URL`.

## How to get an APK

1. Push to `develop`.
2. GitHub Actions → **APK Build** for that SHA (Quality/Test can fail independently).
3. Install **gplay debug** (`org.filewire.connect.debug`).
4. Uninstall previous package first if signatures/ids changed.

## After install

1. Login `@you:matrix.filewire.eu.org`.
2. Notifications troubleshoot: Firebase token → Attempt to fix.
3. Grant full-screen notifications / ignore battery optimization for ringing.

## Calls (in-app)

- WebView Element Call (`https://appassets.androidplatform.net/element-call/index.html`)
- JOIN vs START: see `AGENT_HANDOFF.md` and commit `58b703e9c2`
- Incoming ring is primarily **FCM**; app-open may show timeline **Join**
- Do **not** tap leftover **Join** / Call started tiles after hang-up (MatrixRTC delayed leave)

## CI traps seen on this fork

- Ktlint import order in `PreferencesFlowNode.kt` (`connectprivacy` after `blockedusers`)
- Login tests: default provider title is **not** `matrix.org`
- Duplicate test names in `PreferencesRootViewTest.kt` (About vs Sign out)
- `DefaultElementCallEntryPoint` now needs `MatrixClientProvider` in unit tests
- Expired-RING tests must match “ring only if `hasRoomCall`”

## Git commit identity (PowerShell, no git config)

```powershell
$env:GIT_AUTHOR_NAME="filewire"
$env:GIT_AUTHOR_EMAIL="312208060+filewire@users.noreply.github.com"
$env:GIT_COMMITTER_NAME="filewire"
$env:GIT_COMMITTER_EMAIL="312208060+filewire@users.noreply.github.com"
```
