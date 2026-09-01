# Connect Android — agent handoff (21 Aug 2026, git notes updated 1 Sep 2026)

**Server rebuild (Oracle VPS lost):** start at [`docs/connect-rebuild/README.md`](docs/connect-rebuild/README.md).

This file is for the next AI agent (Claude). Read it before changing call, push, branding, or CI code.

**Working copy:** `E:\Chat V0.1\connect-git`  
**Do not use:** `E:\Chat V0.1\connect-develop` for ongoing work.

---

## Can the user delete `connect-develop`?

**Yes, for git/call work it is unused.**

| Folder | Role |
|---|---|
| `E:\Chat V0.1\connect-git` | **Real repo.** Has `.git`, branch `develop`, remote `https://github.com/filewire/connect`. All Connect branding, push, and call work in this chat landed here. |
| `E:\Chat V0.1\connect-develop` | **Orphan snapshot.** Full Element X-style tree **with no `.git`**. Nothing was committed or pushed from it in this workstream. Safe to delete if disk space matters. |

Before deleting `connect-develop`, the user can optionally zip it as a backup. It is **not** the source of truth.

**Keep:** `E:\Chat V0.1\connect-git` and parent files (`google-services.json`, `firebase.json`, `API Keys.txt`, master prompt markdown).

---

## Product

- **App name:** Connect (Element X Android fork, Compose + `matrix-rust-sdk`)
- **GitHub:** https://github.com/filewire/connect — branch **`develop`**
- **Package:** `org.filewire.connect` / debug `org.filewire.connect.debug`
- **Homeserver:** `https://matrix.filewire.eu.org` (`AuthenticationConfig.MATRIX_ORG_URL` — the constant name is leftover from Element; the value is Filewire)
- **Calls / LiveKit:** `https://rtc.filewire.eu.org`
- **Push:** Sygnal + FCM. App pusher id `org.filewire.connect`. Gateway URL in the app is Docker-internal: `http://matrix-sygnal:5000/_matrix/push/v1/notify` (`PushConfig.FCM_PUSHER_HTTP_URL`). Synapse must reach Sygnal on the Docker network; phone “push loopback” tests against a public URL can still fail.
- **Test accounts (user-reported):** phone `@test1`, laptop Element Desktop `@rakesh` / Sadhana. DM between them.

**User:** bodduna. Prefers coding in-repo, Cursor, GitHub Actions (laptop is weak: i3 / 8 GB — **do not rely on local Gradle/Android Studio**). Git author for this repo (env only, never `git config`): `filewire` `<312208060+filewire@users.noreply.github.com>`.

---

## Hard constraints (do not violate)

- Follow `connect-git/AGENTS.md` (Element X conventions: Timber, `temporary.xml` for new EN strings, **never edit `localazy.xml`**, Compound UI, Metro DI, Appyx + Molecule).
- **Do not touch** app lock / biometric / `features/lockscreen/`.
- **Do not force-push** `develop`.
- **One logical commit per push** when possible; user is frustrated by long CI waits from many tiny commits.
- New English strings → `temporary.xml` (or existing `connect_call_strings.xml` / `connect_strings.xml` if that is already the Connect overlay pattern).
- Do not log message bodies, passwords, keys.
- Sentence-style commit titles (no conventional commits).
- Prefer wrappers over rewriting Element Call / MatrixRTC core (upstream merge risk).

---

## Product vision (user plans, not all done)

From *Connect Android App Development Master Instructions* (`E:\Chat V0.1\Connect Android App Development Master Instructions.md`):

Connect should feel like WhatsApp / Signal / Telegram. Matrix is backend only. Hide Matrix jargon.

**Milestones (grouping, not tiny PRs):** Branding, Privacy, Navigation, Calls, Chat improvements.

**Workflow the user wants:** understand → plan → think (edge cases) → implement complete slice → self-review → wait for approval. Prefer GitHub Actions APK over local builds.

---

## Phases discussed in this workstream

These were an internal call/UX roadmap. Status as of **HEAD `566b9c3772`**.

| ID | Scope | Status |
|---|---|---|
| **A** | Call errors: 45s load timeout, typed errors, Retry / Hang up, Timber (no user content) | **Done** (commit `a39a62dec9` and follow-ups) |
| **B** | Ringing / connecting / busy UX + audible ringback | **Not done** |
| **C** | GSM hold/mute on audio focus loss | **Not done** |
| **D** | Join races, delayed MatrixRTC leave, mutual call, WebView teardown | **Partially done** — hang-up/WebView/START-vs-JOIN iterated many times; **incoming/outgoing still broken in user testing** |
| **E2** | Share music via AudioPlaybackCapture | **Not done** |
| **UI** | Bottom nav: Chats / Calls / Contacts / Settings | **Not done** |

Branding / login / push / privacy hub were done **before** the call deep-dive (see commit list below).

---

## Git state (source of truth)

- **Branch:** `develop`, tracking `origin/develop`
- **HEAD at last call-code push:** `58b703e9c2` — JOIN when room has active call; stop ringing for expired ended calls. Rebuild docs may be a later commit on `develop`.
- Working tree may show `WebViewSafeDestroy.kt` and `connect_call_strings.xml` as modified; last check was **no content diff** (likely CRLF). Discard if they are noise: `git checkout --` those files after verifying `git diff` is empty.

### Connect-relevant commits (newest first)

| SHA | What |
|---|---|
| `58b703e9c2` | JOIN when room has live call; expired RING only if hasRoomCall; ring observer no drop(1) |
| `566b9c3772` | CI: rename duplicate `PreferencesRootViewTest` About vs Sign out |
| `0fa476b487` | JOIN only if answering incoming ring; ring even after late FCM; leftover InCall + force-START can be replaced by RING; login test titles for Filewire; ktlint import order |
| `59c9a159c5` | JOIN if `CallState.Ringing`; close hang-up UI immediately; CI quality fixes |
| `f5f78c6fdd` | Always START_CALL (broke answering a live laptop call) |
| `c6d37c89c4` | Restore imports in call notification resolver |
| `686e1bd44c` | Hang-up after short grace; RING without waiting for `hasRoomCall` |
| `3a02b7d75f` | Wait for MatrixRTC idle after hang-up (later hang-up UI stopped blocking on this) |
| `b3caffa52f` / `a2c220074c` / `a7a3905bdf` / `e86fc09014` / `7fd0b72d89` | Leave / START / WebView / zombie session |
| `a39a62dec9` | Call error UX |
| `6c632293cd` | Video self-view PiP drag in WebView |
| `a00d30f9be` | Docker-internal Sygnal URL |
| `1bb3b4513f` / `42d3397579` / `55c6160fe2` | Notification icon + Firebase + welcome logo |
| `2ef8bae7f0` | Skip login confirmation, FCM gplay, Privacy settings |
| `af27779613` | `org.filewire.connect` package + Firebase/Sygnal ids |
| `842ee19bd5` | Milestone 1 branding |

Install APKs from GitHub Actions **APK Build** for the SHA, not from Quality/Test jobs.

---

## How Element Call works in this app (needed for the bug)

Android does **not** implement native WebRTC for room calls. It loads **embedded Element Call** in a WebView (`https://appassets.androidplatform.net/element-call/index.html`).

`DefaultCallWidgetProvider` → `DefaultCallWidgetSettingsProvider`:

- `hasActiveCall = roomInfo.hasRoomCall && !forceStartNewCall`
- If `hasActiveCall`: widget intent **JOIN_EXISTING** (or JOIN_EXISTING_DM*)
- Else: **START_CALL** (or START_CALL_DM*)

**JOIN_EXISTING against leftover `hasRoomCall` (zombie MatrixRTC `org.matrix.msc3401.call.member`) never reaches `content_loaded`.** UI shows **Please wait**, then timeout (~45s) → Hang up / Retry.

`CallScreenView` treats `!isCallActive` (`isWidgetLoaded`) as Please wait.

Hang-up: send hangup to widget, close UI immediately, 2s grace, then close widget driver (`ElementCallConfig.CALL_HANGUP_MIN_GRACE_SECONDS = 2`). MatrixRTC delayed leave (MSC4140) can keep **Join** tiles and running timers on desktop/phone after hang-up. **Uninstall does not clear server membership.** Tell testers: **do not tap old Join rows.**

Incoming ring is **FCM-only** today: `NotificationResultProcessor.handleRingingCallEvent` → `ElementCallEntryPoint.handleIncomingCall` → `ActiveCallManager.registerIncomingCall` → full-screen `IncomingCallActivity`.

Chat **Join** (timeline / header) always goes through `elementCallEntryPoint.startCall` → `ElementCallActivity`. That is **not** `IncomingCallActivity`.

Notification **Answer** PendingIntent opens `ElementCallActivity` directly (`RingingCallNotificationCreator` → `IntentProvider.getPendingIntent`). It does **not** call `IncomingCallActivity.onAnswer`.

---

## Current call bugs (user still sees these)

Latest user report after installing post-`0fa476` / `566b9c`:

1. **Phone → laptop:** Please wait, then hang-up / retry. LiveKit server log (`c:\Users\Sadhana\Desktop\_matrix-livekit_logs.txt`) showed **only** Electron `@test1` on Windows — **no Android client ever joined LiveKit**. Failure is before media (widget intent / WebView / JWT), not Coturn.
2. **Laptop → phone, app locked or killed:** no ring.
3. **Laptop → phone, app open:** only timeline **Join**, not incoming ring UI.
4. After laptop hang-up, phone **started ringing** and would not stop until dismissed on the phone.

### Root causes already identified (not all fixed)

**A. JOIN only from IncomingCallActivity answer (`0fa476`)**

`CallScreenPresenter` uses JOIN iff `consumeAnsweringIncoming(roomId)`. That flag is set only in `IncomingCallActivity.onAnswer()`.

| Path | `markAnsweringIncoming`? | Intent |
|---|---|---|
| Full-screen incoming → Answer | Yes | JOIN |
| Notification Answer button | **No** | **START** |
| Chat Join / header Join | **No** | **START** |

START against a **live** laptop call (`hasRoomCall=true`) is the Please wait / never-reaches-LiveKit path.

**B. Incoming ring only from push, not live sync**

Foreground sync shows Join because `hasRoomCall` updates. There is no timeline/`RtcNotificationType.RING` listener that calls `registerIncomingCall`. Closed/locked phones depend on FCM + full-screen intent (also needs “use full screen notifications” / battery).

**C. Late FCM always rings 90s (`0fa476`)**

If `expirationTimestamp - now < 0`, code rings for `RINGING_CALL_DURATION_SECONDS` **without** checking whether the call is still live. After laptop hang-up, delayed RING still rings. Observer that cancels ring when `!hasRoomCall` **drops the first emission** (`.drop(1)`), so sticky `hasRoomCall` after hang-up can leave the phone ringing.

**D. Leftover Join tiles**

Server-side delayed leave. Product: ignore old Join; do not treat as a new call.

---

## Intended next code fixes (do these; do not guess)

### Fix 1 — JOIN whenever joining a live room call

In `DefaultElementCallEntryPoint.startCall` (or equivalent, before Activity):

- If `!shouldForceStartNewCall(roomId)` and room `info().hasRoomCall` → `markAnsweringIncoming(roomId)`.

In `ElementCallActivity.setCallData`:

- If `activeCall` is `Ringing` for this room → `markAnsweringIncoming`.

Covers notification Answer, chat Join, header Join.

Keep **START** after **local hang-up** (`forceStartNewCallRoomId`) so recall does not JOIN a zombie session.

### Fix 2 — Expired RING: ring only if call still live

In `registerIncomingCall` when remaining lifetime &lt; 0:

- If `!hasRoomCall` → missed-call notification, **do not ring**.
- If `hasRoomCall` → ring (late FCM, call still up).

### Fix 3 — Ring from live sync when app is in foreground (optional but needed for “app open → Join only”)

On `RtcNotificationType.RING` from timeline/sync, call the same `handleIncomingCall` / `registerIncomingCall` path as FCM.

### Fix 4 — Confirm WebView / JWT if Please wait remains after JOIN is correct

`adb logcat` tags: `CallScreen`, `ActiveCallManager`, `DefaultCallWidgetSettingsProvider`, Chromium.

Look for `intent=START_CALL` vs `JOIN_EXISTING` and `Forcing START_CALL despite hasRoomCall=true`.

### Do not

- Always START (`f5f78c6fdd` broke answer).
- JOIN whenever `CallState.Ringing` (`59c9a159c5` JOINed leftover Ringing / outgoing).
- Block hang-up UI on long MatrixRTC idle poll (user hated &gt;10s hang-up).

---

## Config knobs

`appconfig/.../ElementCallConfig.kt`

- `RINGING_CALL_DURATION_SECONDS = 90`
- `CALL_WIDGET_LOAD_TIMEOUT_SECONDS = 45`
- `CALL_REJOIN_COOLDOWN_SECONDS = 2`
- `CALL_HANGUP_MIN_GRACE_SECONDS = 2`
- `CALL_LEAVE_SETTLE_MAX_SECONDS = 25` (UI does not wait this long)

---

## Key files

```
features/call/impl/.../ui/CallScreenPresenter.kt
features/call/impl/.../ui/CallScreenView.kt
features/call/impl/.../ui/ElementCallActivity.kt
features/call/impl/.../ui/IncomingCallActivity.kt
features/call/impl/.../utils/ActiveCallManager.kt
features/call/impl/.../utils/DefaultCallWidgetProvider.kt
features/call/impl/.../DefaultElementCallEntryPoint.kt
features/call/impl/.../notifications/RingingCallNotificationCreator.kt
features/call/impl/.../utils/IntentProvider.kt
libraries/matrix/impl/.../widget/DefaultCallWidgetSettingsProvider.kt
libraries/push/impl/.../notifications/CallNotificationEventResolver.kt
libraries/push/impl/.../notifications/NotificationResultProcessor.kt
features/messages/impl/.../MessagesFlowNode.kt          # startCall from Join
features/messages/impl/.../timeline/components/ActiveCallTimelineItemView.kt
features/roomcall/impl/.../RoomCallStatePresenter.kt
appconfig/.../ElementCallConfig.kt
appconfig/.../AuthenticationConfig.kt
appconfig/.../PushConfig.kt
```

Tests: `CallScreenPresenterTest.kt`, `DefaultActiveCallManagerTest.kt`, `FakeActiveCallManager.kt`, `DefaultCallWidgetProviderTest.kt`.

Login CI: `AccountProviderDataSourceTest.kt`, `ChangeAccountProviderPresenterTest.kt` — default title is `matrix.filewire.eu.org`; a list entry `https://matrix.org` is **not** `isMatrixOrg` because `isMatrixOrg = (url == AuthenticationConfig.MATRIX_ORG_URL)`.

Preferences CI: `PreferencesRootViewTest.kt` — Sign out test must **not** be named the same as About.

Ktlint: `PreferencesFlowNode.kt` imports must be lexicographic (`about`, `advanced`, `analytics`, `blockedusers`, `connectprivacy`, `developer`, …).

---

## CI notes

- User screenshots of Gradle worker stacks are **not** the real failure. Download job logs.
- Past failures: ktlint import order; login tests expecting `matrix.org`; duplicate test function names in `PreferencesRootViewTest`.
- APK Build can succeed while Quality/Test fail. User should install **APK Build** artifact for the SHA.
- Local `JAVA_HOME` on the Windows machine is often broken; use Actions.

---

## Test plan after the JOIN/ring fixes

1. Fresh DM; **do not tap old Join / Call started**.
2. Phone → laptop: both should connect; LiveKit should show a **second** participant (Android), not only Electron.
3. Laptop → phone, app **closed / locked**: incoming ring UI (full-screen / notification).
4. Laptop → phone, app **open**: ring **or** Join that actually connects (JOIN, not Please wait).
5. Hang up laptop: phone must **stop ringing** without waiting for 90s.

---

## Out of scope / server

- Synapse `max_event_delay_duration`, Sygnal FCM priority — leftover Join timers and delayed push.
- `features/lockscreen/`
- Rewriting Element Call JS (embedded asset) unless logs prove it.

---

## How to continue in Cursor / Claude

1. Open folder **`E:\Chat V0.1\connect-git`** as the workspace root (`move_agent_to_root` if needed).
2. Do **not** switch to `connect-develop`.
3. Implement Fix 1 and Fix 2 first; keep one commit unless CI needs a tiny follow-up.
4. Push `develop` only when the user asks (or if they already asked to ship a call fix).
5. Point the user at the Actions APK for the new SHA.

Prior Cursor transcript (this workstream): agent-transcripts uuid `7b22fe2f-8632-40d5-992a-b061c60b0b74`.
