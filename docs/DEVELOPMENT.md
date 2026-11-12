# Local development

## Prerequisites

| Tool | Version | macOS install |
|---|---|---|
| Java (JDK) | 21 | `brew install openjdk@21` (or [SDKMAN](https://sdkman.io): `sdk install java 21-tem`) |
| Maven | 3.9+ | `brew install maven` |
| Node.js | 22 LTS | `brew install node@22` (or `nvm install 22`) |
| Docker | any recent | [Docker Desktop](https://www.docker.com/products/docker-desktop/) or OrbStack |

## 1. Start the database

```bash
docker compose up -d          # PostgreSQL 16 on localhost:5432 (db/user/password: trafonsjakk)
```

The credentials in `docker-compose.yml` are local-only placeholders. The port is bound to `127.0.0.1`.

## 2. Run the backend

```bash
cd backend
mvn spring-boot:run           # http://localhost:8080
```

- The `dev` profile is active by default (`application-dev.yml`). It points at the Docker database and uses a dev-only JWT secret, so you don't need any environment variables.
- Flyway creates the schema on first start.
- Health check: <http://localhost:8080/actuator/health>
- To override anything, copy `backend/.env.example` to `backend/.env` (git-ignored) and export it, or set the variables in your IDE run configuration.

**Optional (recommended):** add the Maven wrapper so nobody needs Maven installed:

```bash
cd backend && mvn -N wrapper:wrapper && git add mvnw mvnw.cmd .mvn
```

## 3. Run the frontend

```bash
cd frontend
npm install
npm run dev                   # http://localhost:5173
```

Vite proxies `/api` to `http://localhost:8080`, so the browser sees one origin, exactly as in production. Point it elsewhere with `VITE_DEV_API_TARGET` in `frontend/.env.local`.

## 4. Create the first account

Open <http://localhost:5173/register> and use the invite code **`let-me-in`** (the dev `BOOTSTRAP_INVITE_CODE`). Because the users table is empty, that account becomes **admin**. Then create real invite codes on the *Invites* page for everyone else.

## Tests and checks

```bash
# Backend: unit tests + API tests (start the database first: docker compose up -d)
cd backend && mvn verify

# Frontend
cd frontend && npm test && npm run typecheck && npm run lint && npm run check:i18n && npm run build
npm run test:watch            # re-runs tests as you edit
```

CI runs all of this plus a gitleaks secret scan on every push (`.github/workflows/ci.yml`).

### Backend tests

| Kind | Where | Needs |
|---|---|---|
| Unit tests (Glicko-2, pairings, bracket, standings, stats, monthly award rules, small domain rules) | `src/test/java/no/trafonsjakk/{rating,tournament,stats,award,game,auth,common}` | nothing |
| API tests: the real app on a random port, called over HTTP with cookies and CSRF like the browser | `src/test/java/no/trafonsjakk/api` | PostgreSQL |

The API tests use the `test` profile (`src/test/resources/application-test.yml`). They run in their own schema, `trafonsjakk_test`, inside the Docker database, so they never touch your dev data, and they empty all tables before each test. Point them elsewhere with `TEST_DATABASE_URL` / `TEST_DATABASE_USERNAME` / `TEST_DATABASE_PASSWORD`.

If the database isn't running, the API tests are **skipped** with a hint, so `mvn verify` still works offline. In CI (`CI=true`) they're never skipped: an unreachable database fails the build.

| API test class | What it covers |
|---|---|
| `AuthApiTest`, `InviteApiTest`, `AdminApiTest`, `PasswordResetApiTest` | sign-up, login, cookies, refresh, invites, admin actions, password links |
| `GameApiTest`, `TournamentApiTest`, `RatingStatsApiTest`, `GameContextApiTest` | features end to end |
| `SocialApiTest`, `CommentThreadsApiTest` | comments, replies, likes, kudos |
| `AwardsApiTest` | monthly awards: the rules, Norwegian month boundaries, deciding once, frozen results |
| `SecurityApiTest`, `RateLimitApiTest` | attacks: forged tokens, stolen sessions, enumeration, over-posting, hostile input, every endpoint's access rules |
| `InputLimitsApiTest` | every field limit on both sides of the boundary |
| `ConcurrencyApiTest` | requests fired in parallel (double clicks, two tabs, two people) |
| `TournamentInvariantsApiTest` | whole tournaments of 2–32 players with seeded random results |

To add one, extend `support/IntegrationTest`. It gives you `registerAdmin()`, `registerMember(...)`, `bulkUsers(n, prefix)` (fast inserts) with `login(id)`, `game(...)`, `playConfirmed(...)`, `rating(...)` and a `jdbc` template for setting up time-based cases (for example, backdating `created_at` to test auto-confirm). `support/Race` starts several requests at the same instant; `support/Jwts` mints tokens for attack tests.

### Frontend tests

Vitest with jsdom and Testing Library, next to the code they test (`*.test.ts(x)`). `src/test/setup.ts` clears cookies and storage between tests. Test files get their own `tsconfig.test.json` (with Node types); the app's config excludes them, so browser code can't accidentally use Node APIs.

One gotcha: write `beforeEach(() => { mock.mockReset() })` with braces. Vitest treats a function *returned* from `beforeEach` as a teardown hook and calls it after the test.

### Adding or changing UI text

All visible text lives in `frontend/src/i18n/en.ts` and `nb.ts`. Add the key to both (TypeScript fails the build if `nb.ts` is missing it) and use `const { t } = useI18n()` → `t.section.key`. `npm run check:i18n` fails if a component contains hard-coded text. For a new backend error, give the `ApiException` a code (`ApiException.conflict("thing.code", "English fallback")`) and add `errors.codes['thing.code']` in both dictionaries.

## Project layout

```
trafonsjakk/
├── backend/                 Spring Boot API
│   ├── src/main/java/no/trafonsjakk/
│   │   ├── auth/            login, register, JWT cookies, refresh tokens, invites, password links, lockout
│   │   ├── config/          security, JWT cookie filter, typed properties, CSRF handler
│   │   ├── game/            game logging + confirmation lifecycle, auto-confirm, game context
│   │   ├── social/          comments (replies, likes) and kudos
│   │   ├── rating/          Glicko-2 engine, recompute, leaderboard
│   │   ├── award/           player of the month
│   │   ├── tournament/      round-robin & knockout engines, standings
│   │   ├── stats/           profile statistics
│   │   ├── user/ admin/ common/
│   ├── src/main/resources/
│   │   ├── application*.yml
│   │   └── db/migration/    Flyway SQL
│   └── Dockerfile
├── frontend/                React + Vite + Tailwind
│   ├── src/api/             fetch client (CSRF + refresh), endpoints, DTO types
│   ├── src/auth/            session provider, route guards
│   ├── src/components/      ui/ (design system), chess/ (domain widgets), awards/, admin/, brand/, layout/
│   ├── src/chess/           move legality, PGN and move statistics (chess.js)
│   ├── src/i18n/            Norwegian/English dictionaries and formatting
│   ├── src/pages/           one file per route
│   └── vercel.json          SPA routing, /api proxy, security headers
├── docs/                    architecture, API, security, deployment
├── docker-compose.yml       local PostgreSQL
└── render.yaml              Render blueprint for the API
```

## Troubleshooting

| Symptom | Fix |
|---|---|
| Backend: `JWT_SECRET is not set` | You're running without the `dev` profile. Set `SPRING_PROFILES_ACTIVE=dev` or provide `JWT_SECRET`. |
| Backend: `Connection refused` to 5432 | `docker compose up -d` and wait for the health check (`docker compose ps`). |
| Backend: `Schema-validation: missing column` | Your database is from an older migration set. Reset it: `docker compose down -v && docker compose up -d`. |
| Frontend: every action says *You don't have permission* | The CSRF cookie is missing. Make sure you open the app via `localhost:5173` (not `127.0.0.1`) so cookies match. |
| Logged out after every reload | Cookies are `Secure` in prod. Locally the `dev` profile sets `app.cookies.secure=false`; make sure that profile is active. |
