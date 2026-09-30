# Leadfold Backend

Parses messy WhatsApp/form/DM messages into structured lead data (via Claude) and pushes each new lead to Zapier/Make and any CRM the user connects.

## Stack

- Java 17, Spring Boot 3.3.4, Maven
- Spring Data JPA (Hibernate) + PostgreSQL (target: [Neon](https://neon.tech), serverless Postgres)
- Spring Security with two custom auth paths: JWT (dashboard) and static API key (Zapier/Make)
- Claude API (`claude-haiku-4-5-20251001`) for parsing raw messages into structured fields
- [spring-dotenv](https://github.com/paulschwarz/spring-dotenv) to load `.env` locally, same workflow as `.env.example`

## Setup

1. Copy `.env.example` to `.env` and fill in the values.
   - **Database**: create a project at [neon.tech](https://neon.tech), grab the connection string it gives you (`postgresql://user:password@ep-xxxx.neon.tech/dbname?sslmode=require`), then split it into `DATABASE_URL` (as a `jdbc:postgresql://...` URL), `DATABASE_USERNAME`, and `DATABASE_PASSWORD` — see the comments in `.env.example`.
   - **JWT_SECRET**: any random string, at least 32 characters (HS256 requires a 256-bit key).
   - **ANTHROPIC_API_KEY**: from console.anthropic.com.
   - **WHATSAPP_VERIFY_TOKEN**: any string you choose — you'll enter the same value in the Meta App dashboard when you register the webhook.
2. `./mvnw spring-boot:run` (or `mvn spring-boot:run` if you have Maven installed globally) — Hibernate will auto-create the tables on first run (`ddl-auto: update`, fine for this stage; switch to proper migrations before this has real production data in it).
3. API is served under `http://localhost:4000/api`.

## Endpoints

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/auth/signup` | none | Create an account, returns a JWT + the account's API key |
| POST | `/api/auth/login` | none | Returns a JWT + the account's API key |
| GET | `/api/leads` | JWT | List the logged-in account's leads (dashboard) |
| PATCH | `/api/leads/{id}` | JWT | Update a lead's status |
| GET | `/api/webhooks/whatsapp` | none | Meta's webhook verification handshake |
| POST | `/api/webhooks/whatsapp` | none | Meta calls this with incoming WhatsApp messages |
| POST | `/api/webhooks/form/{apiKey}` | apiKey in path | Generic webhook for an embedded form/widget |
| POST | `/api/zapier/hooks/subscribe` | x-api-key header | Zapier/Make calls this when a user turns on a Zap |
| DELETE | `/api/zapier/hooks/subscribe/{id}` | x-api-key header | Called when a user turns off a Zap |
| GET | `/api/zapier/triggers/new-lead` | x-api-key header | Polling fallback + Zapier's "test this trigger" |

## Testing the WhatsApp webhook locally

Meta needs a public HTTPS URL. Easiest local option: `ngrok http 4000`, then register `https://<your-ngrok-id>.ngrok-free.app/api/webhooks/whatsapp` as the callback URL in the Meta App dashboard, with the verify token matching `WHATSAPP_VERIFY_TOKEN`.

## Not built yet (next steps)

- The actual Zapier Platform CLI app (the Trigger definition that appears inside Zapier's UI) — the REST Hook endpoints above are ready for it, but the CLI project itself (`zapier init`) hasn't been scaffolded. Needs a Zapier developer account to do.
- Make.com: for now, users can point Make's generic "Webhooks" module at `/api/zapier/hooks/subscribe` manually; a proper Make custom app is a later step once there's traction.
- Proper DB migrations (Flyway/Liquibase) instead of `ddl-auto: update`.
- A UI for connecting a WhatsApp number / creating a `WhatsAppConfig` row (currently has to be inserted manually).
- Rate limiting / abuse protection on the public webhook endpoints.

See `CLAUDE.md` in this repo for the fuller project context (why this exists, the business decisions behind it, and what's already been ruled out).
