# Secrets and accounts — recreate, do not commit

**Never push** LiveKit secrets, Postgres passwords, Synapse signing keys, Cloudflare tunnel tokens, or Firebase **service account private keys** to GitHub.

## Recreate on the new server

| Secret | How |
|---|---|
| Postgres `POSTGRES_PASSWORD` | New strong password; same in compose + `homeserver.yaml` |
| Synapse registration / admin | `register_new_matrix_user` |
| Synapse signing key | Generated with `synapse generate` unless you restore old `*.signing.key` |
| LiveKit key/secret | `openssl` as in 02-new-server-backend.md |
| Coturn `static-auth-secret` | New random hex |
| Cloudflare tunnel | New tunnel in Zero Trust |
| Firebase service account | Console → Project settings → Service accounts → Generate new key → `firebase.json` on VPS only |

## Laptop files (offline backup — copy to a password manager)

These existed next to the repo, **not** in Git:

| File | Contains |
|---|---|
| `E:\Chat V0.1\API Keys.txt` | LiveKit API key + secret used on the **old** cluster |
| `E:\Chat V0.1\firebase.json` | Service account for project `matrix-chat-0143` (Sygnal) |
| `E:\Chat V0.1\google-services.json` | Old client for package `org.amor.chat` — not the Connect package |

Treat `API Keys.txt` and `firebase.json` as **compromised if the laptop is shared**; rotate LiveKit and Firebase keys when the new server is up.

## Firebase (public client IDs — already in the Android repo)

Safe to document; they are in `BuildTimeConfig` / `firebase.xml`:

- Project: `matrix-chat-0143`
- Sender: `247291343746`
- Release app: `1:247291343746:android:c8165338a10fd4f473559b`
- Debug app: `1:247291343746:android:9bb275e8eb91d66473559b`

Android API keys in `firebase.xml` are **client** keys (restricted in Google Cloud if you set app restrictions). Still do not paste **private_key** PEM anywhere public.

## Matrix users (old Synapse — gone without DB backup)

Recreate:

- `@test1:matrix.filewire.eu.org` (phone)
- `@rakesh:matrix.filewire.eu.org` (desktop)

Or new names; update tests only if you hardcode them (you generally should not).

## GitHub

- Org/user: **filewire**
- Repo: **connect**
- Actions: APK Build on `develop`
