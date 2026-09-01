# Connect — rebuild index

Use this folder if the **Oracle VPS is gone** and you need to put **Synapse, LiveKit, Sygnal, DNS, Firebase, and the Android app** back online.

| Document | Purpose |
|---|---|
| [01-overview.md](01-overview.md) | What Connect is, hosts, packages, what was lost vs what is in Git |
| [02-new-server-backend.md](02-new-server-backend.md) | Step-by-step new VPS: Docker, Postgres, Synapse, LiveKit, JWT, Coturn, Sygnal, Cloudflare |
| [03-android-app.md](03-android-app.md) | Frontend: repo, branding, push, calls, CI, how to ship APKs |
| [04-secrets-and-accounts.md](04-secrets-and-accounts.md) | What to recreate (never commit real keys) |
| [05-what-we-built.md](05-what-we-built.md) | Product work, milestones, call bugs, git history |
| [../../AGENT_HANDOFF.md](../../AGENT_HANDOFF.md) | Call-code handoff for the next AI |

**GitHub (source of truth for the app):** https://github.com/filewire/connect — branch `develop`

**Official Element Call self-hosting (keep in sync with current docs):**  
https://github.com/element-hq/element-call/blob/livekit/docs/self_hosting.md
