# Leadfold — project context

Read this before doing anything else in this repo. It's the "why", not just the "what" — the README covers setup/endpoints.

## What Leadfold is

A narrow, single-workflow SaaS: it parses messy incoming messages (WhatsApp, web form, DM) into structured lead data using Claude, then pushes each new lead into whatever CRM/spreadsheet the user already uses, via a Zapier/Make integration. Positioned explicitly as **"replace the freelancer/VA who currently does this data entry manually"** — not a generic omnichannel CRM.

Deliberately **vertical-agnostic in the code** (no hardcoded "real estate" or "clinic" fields) even though the go-to-market targets one vertical first. `Lead.extractedFieldsJson` is a free-form JSON bag for whatever vertical-specific fields Claude finds (budget, preferred_date, service_type, etc.) rather than fixed columns.

## Why this exists (business context)

- Built by a solo technical founder with a full-time job (~10-15 hrs/week available), side-project mode.
- Explicitly chose to **skip manual concierge-style validation** (no time for it) — the risk of that was flagged clearly (could build something nobody wants), but the founder decided to proceed anyway and use early marketplace signups as a stand-in validation signal instead.
- Real personal deadline: founder wants extra income for a wedding next year, or at minimum for this to become steady side income. Was advised to treat **freelance work as the near-term income source and this SaaS as a longer-term side bet**, not the other way around — founder explicitly overrode that and chose to focus on Leadfold first, deferring freelance until it has a more mature plan/positioning.
- **Known competitor to be aware of**: [CekatAI](https://cekat.ai) — a funded Indonesian omnichannel AI CRM for WhatsApp/Instagram/TikTok with 3,000+ business customers. Occupies similar territory. Differentiation strategy going forward should lean on narrow vertical focus + the Zapier/Make-native distribution angle, not try to out-feature a funded omnichannel competitor.

## Go-to-market plan (affects product priorities, not just marketing)

Two markets, same product, different distribution layer:

- **Global**: distribute via the **Zapier and Make.com app directories** (not cold sales, not SEO) — modeled on DropCommerce, a solo/evenings-and-weekends Shopify app that reached $78k CAD MRR in 3 years purely through Shopify App Store organic discovery. This is *why* the backend's Zapier-facing endpoints (`/api/zapier/**`) exist and were built before a polished dashboard UI — the marketplace listing is the primary planned acquisition channel, so the integration surface is the priority.
- **Local Indonesia**: distribute through the founder's freelance/agency network (Projects.co.id, Sribulancer, local Facebook UMKM groups) — those same contacts double as first customers/resellers.
- Ruled out as primary channels (with reasoning, from actual research, not guesses): SEO/content (3-6+ months to any traffic, 12+ months to revenue — too slow for this founder's constraints), cold email automation (matches an already-documented $0-revenue pattern for cold SMB outbound), build-in-public (needs years of consistent posting to compound), Product Hunt (one-time low-quality traffic spike only).
- Pricing: PPP-style — local IDR tier priced ~65-80% below the global USD tier, anchored against what a human VA doing this task costs the customer (not against cheap-SaaS pricing).

## Tech stack and why

- **Java 17 + Spring Boot 3.3.4, Maven** — explicit founder choice (an earlier pass assumed a Node/Express/Prisma stack without asking first; that was wrong and had to be discarded — don't assume the stack, ask first, every time). Java 17 rather than 21 because that's the JDK actually installed on the founder's machine (`JAVA_HOME` points at Eclipse Adoptium 17) — check before bumping.
- **Postgres via Neon** (serverless Postgres) — relational, not the graph-DB "Neo4j" that the abbreviation "Neo" could have also meant; this was explicitly disambiguated with the founder.
- **Spring Data JPA**, `ddl-auto: update` for now (fine pre-production; replace with Flyway/Liquibase before real user data exists).
- **Two separate auth mechanisms**, each its own Spring Security filter chain matched by path prefix (see `SecurityConfig`):
  - JWT (`JwtAuthFilter`) for `/api/leads/**` — the dashboard.
  - Static per-account API key (`ApiKeyAuthFilter`, header `x-api-key`) for `/api/zapier/**` — external integrations. Deliberately NOT OAuth — simpler, and sufficient for one Zapier/Make connection per account at this stage.
- **Claude API called directly via Spring's `RestClient`** (raw HTTP to `api.anthropic.com/v1/messages`), not a third-party Java SDK wrapper — avoids depending on an unverified Maven artifact.
- `extractedFieldsJson` stored as a raw JSON string column (`TEXT`), not a native Postgres `jsonb` type — simplest thing that works for now; revisit if querying inside that JSON becomes necessary.

## Current state

Built: entities (`User`, `Lead`, `ZapierSubscription`, `WhatsAppConfig`), repositories, JWT + API-key auth, `LeadParserService` (Claude call), WhatsApp webhook parsing, generic form webhook, Zapier REST Hook endpoints (subscribe/unsubscribe/poll), CORS config for the Next.js frontend at `localhost:3000`.

Not built yet — see README's "Not built yet" section for the concrete list (the actual Zapier Platform CLI app, Make custom app, DB migrations, a UI for connecting a WhatsApp number, rate limiting).

## Working conventions

- This founder wants tech-stack and architecture decisions **confirmed explicitly before code gets written** — don't assume, even when a choice seems obvious or "standard." Ask first.
- Keep the product vertical-agnostic in code even as the GTM plan picks one vertical to launch with first.
