# Security model

Trafonsjakk is a small private app, but it stores passwords and is reachable from the internet, so it follows the same baseline as a production service.

## Threats and mitigations

| Threat | Mitigation |
|---|---|
| **Strangers signing up** | Registration requires an invite code. Members make single-use codes (7-day expiry); admins can make one code for a group, with a limit of 1–500 people and an expiry of up to 90 days. Each use is counted under a row lock, so a code never lets in more people than its limit. Codes are stored as SHA-256 hashes. Each member can hold at most 5 active codes; disabling a member revokes their unused codes. The invite is checked **before** the username, so someone without a valid code can't use the sign-up form to find out which usernames exist. Two people using the same code at the same moment: one gets in, the other gets "invalid code". |
| **Password theft from DB** | BCrypt (cost 12). Passwords are 10–72 bytes (BCrypt's input limit is enforced, not silently truncated). |
| **Brute-force login** | Per-username lockout: 5 failures → 15 min, counted case-insensitively and ignoring spaces, and unaffected by the client's IP. The same lockout guards the current-password check when changing a password, so a stolen session can't be used to guess it faster. Per visitor address: 20 failed logins / 15 min and 10 registrations / hour. Per edge address: 300 failed logins / 15 min and 100 registrations / hour. Unknown usernames fail and lock out exactly like real ones, a disabled account with a wrong password looks like any failed login, and BCrypt runs either way (no user-enumeration by message or timing). **Where the addresses come from** (`ClientAddress`): Render overwrites `True-Client-IP` with the address that connected to it (the *edge* address), which can't be faked; the *visitor* is the `X-Forwarded-For` entry just before that address, which Vercel sets and visitors can't change. The leftmost `X-Forwarded-For` entry is never used, because whoever sends the request picks it. Someone calling the Render URL directly can still make up the visitor entry, but not the edge address, so the edge limits cap them; everyone who comes through the site shares Vercel's edge addresses, which is why those limits are generous. `RateLimitApiTest` covers both routes. Counters are in memory, so a restart resets them. |
| **Token theft via XSS** | JWTs live in `HttpOnly` cookies that JavaScript can't read. The access token (`tf_at`, 15 min) is scoped to `Path=/api`; the refresh token (`tf_rt`, 30 days) is scoped to `Path=/api/auth` with `SameSite=Strict`. Both are `Secure` in production. |
| **Refresh-token replay** | Refresh tokens are random 256-bit values stored hashed. Every refresh rotates the token. Reusing an already-rotated token revokes the whole token family (all sessions from that login). |
| **CSRF** | Spring Security double-submit cookie: the server sets `XSRF-TOKEN` (readable by JS) and requires a matching `X-XSRF-TOKEN` header on every state-changing request, **including for logged-in users**. Cookies are `SameSite`, and the app is same-origin, so this is defence in depth. The access token is read from its cookie by our own `CookieJwtAuthenticationFilter`, not Spring's OAuth2 resource-server support: that support treats any request carrying a token as a "bearer" request and exempts it from CSRF, which is only safe for tokens sent in an `Authorization` header, not for cookies the browser attaches automatically. `AuthApiTest` checks that writes without the header, or with a guessed one, get 403. |
| **XSS** | React escapes all output; the code never uses `dangerouslySetInnerHTML`. Strict Content-Security-Policy on the SPA (`script-src 'self' 'wasm-unsafe-eval'`, `worker-src 'self'`: no inline scripts, no JavaScript eval, no third-party origins; fonts are self-hosted). `'wasm-unsafe-eval'` is there only so the Stockfish engine, served from our own origin, can compile its WebAssembly; `config.test.ts` checks that `'unsafe-eval'` never appears. The API sends `Content-Security-Policy: default-src 'none'`. |
| **SQL injection** | Spring Data / JPQL, or `JdbcTemplate` with bound parameters. The only SQL built from strings joins constants (the advisory-lock key, a fixed `order by`). The awards `month` parameter must match `YYYY-MM` before it's parsed. |
| **Mass assignment / over-posting** | Controllers accept dedicated request DTOs (Java records) validated with Bean Validation, never entities. |
| **Privilege escalation** | Role checks in `SecurityConfig` (`/api/admin/**` → `ADMIN`) and object-level checks in services (only a game's players or an admin can confirm, only the creator or an admin can start/cancel a tournament, …). |
| **Stolen or stale sessions** | Every access token carries its login session (`sid`). On every request the filter checks, in one indexed query, that the session is still live, the account enabled and the role unchanged. So these end the session **immediately** rather than when the 15-minute token expires: logout; a password change (other devices); an admin disabling the account; a role change; and detected refresh-token theft (reuse of a rotated token revokes the whole session, including the thief's copy). A validly signed token that isn't tied to a live session is rejected, which also limits the damage if the signing key ever leaked. |
| **Clickjacking / sniffing** | `X-Frame-Options: DENY`, `frame-ancestors 'none'`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`, HSTS in production. |
| **Information leakage / odd input** | RFC 7807 errors without stack traces, always with a stable `code`, including for 401/403 from the security filters (`auth.not_logged_in`, `forbidden`, `csrf`) and Spring's own errors (`not_found`, `method_not_allowed`, …). `server.error.include-*` disabled; Actuator exposes only `health`, without details. Text fields reject control characters (`@PlainText`) and NUL characters in URLs are refused, so they can't reach PostgreSQL. No CORS headers for other origins. |
| **Oversized requests** | `RequestSizeLimitFilter` refuses any request body over 128 KB with `413` (`request.too_large`) before it's read: by `Content-Length`, and by counting bytes for chunked uploads. The largest real request (a game with a long move list) is far below that; `HardeningApiTest` checks both. |
| **Leaked secrets in Git** | No secrets in the repo, ever. All secrets come from environment variables. `.gitignore` blocks `.env*` (except `*.example`), keys and local config. CI runs **gitleaks** on every push. The app refuses to start in production if `JWT_SECRET` is missing or shorter than 32 bytes.. As a second line of defence, `DeploymentSafetyCheck` stops the app from starting against any non-local database while it still has the public development JWT secret or bootstrap code, which is what would happen if `SPRING_PROFILES_ACTIVE=prod` were ever missing on Render. |
| **Forgotten passwords** | There's no email. An admin makes a one-time link (256-bit random token, stored only as a SHA-256 hash, valid 24 hours, row-locked when used so it can't be used twice) and sends it to the person. The admin never sees or sets the password. Using it ends every session of that person, so anyone else who was logged in as them is thrown out, and clears a login lockout. A new link cancels the previous one; disabling the account cancels it too. The reset form needs the CSRF header like every other write. The page leaves the token out of the browser history after use. |
| **Comments and kudos** | Plain text only (React escapes it; the API returns JSON, never HTML), 1000 characters, no control characters. Only the author edits a comment; the author or an admin deletes it. A reply must point at a comment on the same game. A deleted comment kept for its replies returns no author or text. Kudos and comment likes are one row per person, inserted with `on conflict do nothing`, so double clicks can't inflate counts, and you can't like your own comment. |
| **Engine and shared images** | Stockfish runs in a Web Worker in the user's browser, from our own origin (no CDN). It only receives positions over the UCI text protocol, and its results stay in that browser's `localStorage`; nothing about the analysis reaches the server. Year-in-review images are drawn from the page in the browser (`html-to-image`) and shared through the device's own share sheet or saved as a file; nothing is uploaded. The year-in-review endpoints are read-only and need a login like everything else. |
| **Race conditions** | Changes to a tournament (joining, starting, results updating the bracket or table) lock the tournament row, and logging a tournament game locks the match, so simultaneous requests are handled one after another. A 32-player cap can't be overrun by people joining at the same moment, and confirming the last games at once still completes the tournament. Monthly awards are written once per month with `on conflict do nothing`, so two requests deciding the same month at once can't store two winners. Leftover optimistic-lock conflicts become a 409 `concurrent_change`, never a 500. `ConcurrencyApiTest` fires these requests in parallel. |
| **Weak dependencies** | Dependabot weekly for Maven and npm, monthly for GitHub Actions and the Docker base image. `npm audit --omit=dev` is clean. Spring Boot is on 4.1.2, which brings Tomcat 11.0.27 for the October 2026 Tomcat advisories. |

## Secrets inventory

| Secret | Where it lives | Notes |
|---|---|---|
| `JWT_SECRET` | Render env var | ≥ 32 random bytes, base64. Generate with `openssl rand -base64 48`. Rotating it logs everyone out. |
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | Render env var | From Neon. |
| `BOOTSTRAP_INVITE_CODE` | Render env var, **temporary** | Only works while the users table is empty. Remove it after creating the first admin. |

The local-development defaults in `application-dev.yml` and `docker-compose.yml` (e.g. the `trafonsjakk` DB password and a dev-only JWT secret) only work on `localhost`. They're deliberately obvious placeholders: the `prod` profile rejects the dev JWT secret, and `DeploymentSafetyCheck` refuses to start with either placeholder on a database that isn't on `localhost`.

## If a secret leaks

1. Rotate it at the source (Neon password reset / new `JWT_SECRET`).
2. Update the Render env var and redeploy.
3. If it was committed: rotating is what matters. Rewriting Git history is optional cleanup, because the old value must be treated as public.

## Personal data

The app stores usernames, real names, games, comments, likes and kudos. Anyone can download all of their own data from Settings (`/users/me/export`) and delete their account (`/users/me/delete`, password required): their name, username, password, comments, likes and kudos are removed, and their games stay under "Slettet spiller" because they're part of other people's ratings. Error alerts (Sentry, optional) send no user data, IP address, cookies, headers, request bodies or URL query strings.

## Last review (November 2026)

Checked: the whole Git history with gitleaks (no secrets), dependencies (above), every endpoint's access rules, the cookie and CSRF setup, error responses, the Vercel and Render headers, the Docker image (non-root, JRE only) and the CI permissions (read-only).

Fixed in that review: rate limits that could be dodged by faking `X-Forwarded-For` at the Render URL, no cap on request size, no lockout on the current-password check, no guard against starting with development secrets, and the Tomcat advisories.

Accepted trade-offs:
- The per-username lockout means anyone can lock a known username out for 15 minutes by guessing wrong. That's the price of stopping password guessing; the real user can still get in with a reset link from an admin.
- Visitors who come through the site share Vercel's few edge addresses. Someone using about 15 different addresses at once could use up an edge limit and block logins through the site for 15 minutes. The limits are set high to make that impractical, and low enough to cap someone hammering the Render URL directly.
- Rate-limit counters live in memory, so they reset when the free Render instance restarts.
- GitHub Actions are pinned to version tags, not commit SHAs.

## Reporting

This is a private hobby project; report issues to the repository owner directly.
