# Trafonsjakk — Plan & Roadmap

## Product decisions

These were agreed before any code was written.

| Topic | Decision |
|---|---|
| Name | **Trafonsjakk** |
| Audience | One private friend group (≈ 5–50 people) |
| Rating system | **Glicko-2**, updated after every confirmed game (lichess-style), with an inactivity RD increase |
| Logging a result | A player (or an admin) logs it → **the opponent confirms** → it counts. No reply within **48 h** → auto-confirmed. The opponent can dispute → an admin resolves. |
| Sign-up | **Invite codes only.** Any member can create single-use codes that expire after 7 days. The very first account uses a bootstrap code from an environment variable and becomes admin. |
| Roles | `USER`, `ADMIN`. Admins resolve disputes, correct or void results, manage users and can cancel any tournament. |
| Tournaments | Round-robin (single or double) and single-elimination. Created by any member; started by the creator or an admin. Tournament games are rated. |
| UI language | **Norwegian (bokmål) by default, English available**; switch anywhere, remembered per device. Server errors are translated via error codes. |
| Look | **Light "Trafon facade" theme** (cream, Trafon red, slate) with a dark option, inspired by the Trafon building; no third-party branding |
| Hosting | Vercel (frontend + `/api` proxy) · Render free web service (Docker) · Neon free PostgreSQL |

## Assumptions (change these if they're wrong)

- Over-the-board games are played without a clock. Online games (added for the Christmas holidays 2026) have their own rating, one pool for all online time controls including daily games, started from each player's over-the-board rating. Rated online games still count for statistics, head-to-head, rivalries, monthly awards and the year in review; only the rating is separate.
- Knockout draws are replayed with colours reversed until there's a decisive result (no armageddon logic). Every replay is rated.
- In a round-robin, all pairings can be played in any order; rounds are a suggested schedule.
- A bye in a round-robin is a rest (0 points, not counted as a game).
- Display ratings are rounded to whole numbers; RD is shown as "±".

## Build phases

### Phase 0 — Foundations ✅
- Repo layout (`backend/`, `frontend/`, `docs/`), `.gitignore` that blocks secrets, `.env.example` files
- Docker Compose for local PostgreSQL
- CI (GitHub Actions): backend `mvn verify` against a PostgreSQL service, frontend typecheck + lint + tests + build, gitleaks secret scan
- Dependabot for Maven, npm and Actions

### Phase 1 — Database & auth ✅
- Flyway `V1__init.sql` (all tables, constraints, indexes)
- Users, invite codes, bootstrap admin
- Login/register/refresh/logout with JWT in HttpOnly cookies, refresh-token rotation with reuse detection
- CSRF (double-submit cookie), login lockout, IP rate limits, security headers, request size limit, startup check for development secrets

### Phase 2 — Games & ratings ✅
- Log / confirm / dispute / withdraw / admin resolve
- Glicko-2 engine + full recompute with advisory lock
- Auto-confirm scheduler + lazy sweep
- Leaderboard

### Phase 3 — Tournaments ✅
- Round-robin scheduler (single/double) with colour balancing
- Seeded knockout bracket with byes, draw-replay handling
- Event-driven match re-evaluation, auto-completion, standings with Sonneborn–Berger

### Phase 4 — Statistics ✅
- Profile dashboard: rating graph, W/D/L, colours, streaks, head-to-head, nemesis, best win, openings, activity

### Phase 5 — Frontend ✅
- Design system (tokens, components), app shell, all pages from [ARCHITECTURE.md §4.2](ARCHITECTURE.md#42-routes)
- Skeletons, empty states, toasts, mobile tab bar

### Phase 5b — Norwegian + Trafon redesign ✅
- Typed `nb`/`en` dictionaries, locale-aware formatting, language switch, translated server errors
- `check:i18n` guard in CI (key parity + hard-coded text scan)
- Trafon facade palette, arched-window motif, building illustration, contrast-checked dark theme

### Phase 6 — Ship
- [ ] Generate the Maven wrapper (`mvn -N wrapper:wrapper`) and commit it
- [x] Create Neon DB → Render service → Vercel project ([DEPLOYMENT.md](DEPLOYMENT.md)): live
- [ ] Create the bootstrap admin, then remove `BOOTSTRAP_INVITE_CODE`
- [ ] Invite the group

## Verification status

| Part | Verified |
|---|---|
| Domain logic (Glicko-2, round-robin, knockout, standings, stats, monthly awards, year in review, move judgements) | ✅ Unit tests. Glicko-2 matches Glickman's published example (1464.06 / 151.52 / 0.05999). Round-robin colours are checked for every field size up to 32: balanced, never three in a row, also across the two halves of a double round-robin. |
| Spring wiring, security, database | ✅ 537 backend tests. API tests run the real app against PostgreSQL: the auth, game, tournament, invite and admin flows; **attacks** (forged, unsigned, expired and self-promoted tokens, stolen sessions, CSRF, user enumeration, lockout evasion (including faked forwarding headers), oversized requests, password guessing through the change-password form, starting with development secrets, over-posting, CORS, actuator, malformed and hostile input, an authorisation check of every endpoint); **limits** on every field, both sides of the boundary; **races** (double clicks, two tabs, two people at once); complete randomized **tournaments** of 2–32 players checked against bracket and table invariants (and Swiss tournaments of 3–16: pairing, byes, colours, Buchholz); and **ratings** (leaderboard, provisional players, inactivity, peak, recompute consistency); multi-use invite codes (including six people racing for three places), password reset links, comments (replies, likes, deleted threads), kudos, adding moves, the game context (expected score, upsets, head-to-head) and monthly awards (the rules, tie-breaks, Norwegian month boundaries, each month decided exactly once even with simultaneous readers, decided months that don't change afterwards) the year in review (every highlight, Norwegian year boundaries, empty years), live games (recording, two phones saving at once, finishing into a normal game, idle games), rivalries (record by colour, streaks, upsets, best games, either way round), the opening explorer's data (first moves, typed names, only confirmed games), push notifications (encryption checked by decrypting as a browser would, the VAPID signature, who gets them, forgotten browsers, only real push services), saved engine analyses (shared review, validation, forgotten when the moves change, profile accuracy, the most-accurate award, calibration, and the server's accuracy matching the website's), Google Chat messages (against a stand-in webhook on the test machine: the texts, one thread per live game, nothing for refused requests or old games, which links are accepted, the admin test message) and your own data (export, account deletion with its lockout, the last admin, anonymised games). |
| Frontend | ✅ typecheck, lint, `check:i18n`, production build and 406 Vitest tests: API client, session handling (incl. a sleeping server), route guards, login redirect safety, the log-game form, game actions, registration, safe rendering of user text, deployment headers (`vercel.json`, `index.html`), translation completeness (every backend error code; no English left in Norwegian), the move recorder (tap moves, illegal taps, promotion, undo, typed/pasted PGN with the first illegal move pointed out, checkmate vs. result), comments (threads, replies, likes, Ctrl+Enter), the replay viewer and its move statistics, the game context panel, player of the month (home card, standings, profile badges), the year-in-review stories (cards, navigation, sharing, the December banner), the opening explorer (move tree, transpositions, naming by family, one player's side and colour), engine analysis (UCI parsing, mate handling, move judgements, accuracy, the "played like" estimate, caching, stopping, a failing engine, using and saving the shared review), kudos, invites, the password reset page, live games (saving every move, taking the other phone's moves, following, stepping back and returning to live, live Stockfish lines, logging the result), analysing a finished game with one engine (review first, then the board's position; remembered reviews; your own moves by dragging, tapping or playing an engine line), downloading your data and deleting your account, the screen wake lock, error-report scrubbing and the app manifest, the error pages (crash with error code and recovery, new-version reload, 404s), error codes in messages and reports, and retry rules. A 57-step browser test against a mock API also passed in both languages. |

### Bugs found by the tests (fixed)

- **CSRF wasn't enforced for logged-in users.** Spring's resource-server support exempted cookie-authenticated requests. Replaced by a cookie filter; see [SECURITY.md](SECURITY.md).
- **A demoted admin kept admin rights** (and a disabled member kept access) until their 15-minute token expired. The account is now checked on every request; disabling also revokes the member's unused invite codes.
- **Round-robin colours:** some field sizes (8, 12, 16 players) gave a player the same colour three rounds running, and a double round-robin could do so across its two halves. Now uses Berger tables.
- **Knockout replays** were offered with the original colours although the bracket says draws are replayed with colours reversed.
- **Locale bugs:** invite codes, seeding and tie-breaks used the server's default locale for upper/lower case (wrong under e.g. Turkish).
- **Creating a tournament without `doubleRoundRobin`** failed with an untranslatable 400; malformed request bodies now return `request.malformed`.
- **Frontend:** if fetching the CSRF cookie failed once (offline, or the server waking up), every later save failed until the page was reloaded.

Second round (security, races, limits):

- **Sessions couldn't be revoked.** Logout, a password change and detected refresh-token theft left already-issued access tokens working for up to 15 minutes, and a token minted with the signing key worked without any login. Access tokens are now tied to a live session (`sid`).
- **Sign-up revealed which usernames exist** to anyone, without a valid invite code.
- **Races:** two people joining at the same moment could put **33 players** in a 32-player tournament; confirming the last round-robin games at the same moment left the tournament **stuck "in progress"** with no champion; confirming two semifinals at once crashed with a **500**; simultaneous sign-ups with the same name, or both players logging the same tournament game, got a vague "conflict" instead of a clear message.
- **A player disabled mid-tournament** blocked it forever: nobody could record their games. Admins can now record tournament results for them.
- **NUL characters** in names, notes and so on reached PostgreSQL and came back as a misleading 409.
- **Errors without a code:** bad ids, wrong methods or content types, unknown paths, and every 401/403 from the security layer had no translatable code (401/403 had no body at all).
- **A stale duplicate access cookie** could shadow a fresh one and cause a refresh loop.
- An empty ECO code was rejected although the API treats it as "none".
- **Frontend:** a sleeping server (free hosting) sent logged-in users to the login page; logging out while offline left an unhandled error; the login page could dead-end on a malformed return address.

Security review (November 2026, after going live):

- **Per-IP limits could be dodged.** With `forward-headers-strategy: framework` the "client IP" was the leftmost `X-Forwarded-For` entry, which anyone calling the Render URL directly can set, so every guess could come from a new made-up address. Limits now use Render's `True-Client-IP` and the entry just before it (`ClientAddress`).
- **No limit on request size:** a huge body to the public login endpoint was read into memory. Now refused over 128 KB.
- **The current-password check on "change password" had no lockout**, so a stolen session could guess the password freely. It now shares the login lockout.
- **Nothing stopped a production start with development secrets** if `SPRING_PROFILES_ACTIVE=prod` went missing; now the app refuses to start.
- **Tomcat advisories (October 2026):** Spring Boot 4.1.1 → 4.1.2.

## Added after launch (from members' feedback)

- Invite codes for several people (admin): own limit (1–500) and expiry (1 hour to 90 days).
- One-time password links an admin sends to someone who forgot their password.
- Comments and kudos on games.
- Game page: replay the moves (buttons, arrow keys or tap a move) with move statistics; expected score, upsets and head-to-head before the game; replies and likes on comments.
- Recording moves on a board or as text/PGN, checked for legality; moves can be added after the game is logged.
- A "Found a problem?" page for reporting bugs by email.
- Engine analysis in the browser (Stockfish 18, optional, only for games with moves): blunders, mistakes and inaccuracies with what was better, an evaluation graph and bar, the engine's move as an arrow, accuracy per player.
- "Årets sjakk": a year in review for every player and for the group, as story cards that can be shared as images; on the home page in December and January.
- Live games: record the moves on a phone while you play (or just start the game and tap the result), with others following the board. Saved after every move; two phones can't overwrite each other.
- Live analysis for spectators: follow a live game move by move, step back and return to live, and switch on Stockfish (evaluation bar, best-move arrow and the three best lines, updating as the game goes on). Players don't get it during their own game.
- Live Stockfish when going through your own games: the three best lines in each position, and try your own moves on the board ("what if I'd played…?") with the engine following.
- Analysis redesigned: one **Analyse** button does it all with a single Stockfish (it used to run two at once and stutter), a calmer layout, and a board that no longer redraws on every engine update.
- TV mode: a full-screen scoreboard for a big screen at the venue (live boards, ratings, latest results).
- Installable app (PWA): home-screen icon, opens without the browser bar, screen stays on during live games and on the TV, a friendly page when offline.
- Download your data and delete your account (your games stay, under "Slettet spiller").
- Optional error alerts with Sentry (no personal data sent).
- Error pages drawn on a new, more faithful Trafon facade: 404s with the code on the house-number plate (also for a missing game, player, tournament or live game), a "fuse blew" page with an error code when something crashes, an automatic reload after a new deploy, and an offline banner. Unexpected server errors carry an error code that's also in the server log; a sleeping database answers 503 so the app waits and retries.
- "Played like ≈1450": a rough rating for each player's play in an analysed game, from the accuracy, with a note that one game can be far off.
- Analysis saved on the server: the first review of a game is shared with everyone, profiles show average accuracy and the best-played game, a third monthly award (*most accurate*), and "played like" is fitted to the group's own ratings once there are enough analysed games. Player accuracy now uses lichess's game-accuracy formula.
- Swiss-system tournaments (monrad): a set number of rounds, each paired automatically from the scores so far (no rematches, byes for the lowest-ranked, balanced colours), with Buchholz and Sonneborn–Berger tie-breaks.
- Rivalry pages: two players against each other, with the score, streaks, the biggest upset, both ratings on one chart, their best-played games and every game between them.
- Opening explorer: the group's openings as a move tree with results (transpositions merged), per player and colour, the games behind each line, and the most played openings by name (lichess's open opening list).
- Push notifications: members can turn them on per device in Settings and get "Ola logged a game against you" when a game waits for their confirmation (Web Push, encrypted, no third-party library).
- Google Chat: a message in the group's Chat space when a live game starts (with a link to follow it) and when a game is logged, the result in the same thread as the live game. Optional; admins can send a test message.
- "Played like" fixed: it showed club players as ≈2400–2600 after an easy win. The estimate is now measured on 432 rated online games analysed exactly like here (the rating that players who play that accurately usually have), so it stays between about 1200 and 1850 and says so; on the group's own scale it never goes above the best or below the lowest rating in the group. The same measurement confirmed the accuracy itself (lichess's formula, stable from depth 8 to 18), so it is unchanged.
- Phones fixed where things were cut off: tables (ratings, player of the month, most accurate, head-to-head, the Monrad table, members in admin) no longer hide their right-hand columns, the players in a new tournament keep their rating in view, tournaments show their status and winner, long names wrap instead of shrinking to a few letters (game list, home page, live and online games, tournament pairings), panel headings make room for their buttons, buttons keep their label on one line, and the tab bar's labels fit. Checked on every page at 320, 360, 390 and 430 pixels wide with long names: no page scrolls sideways and nothing is clipped, except the knockout bracket (which scrolls on purpose) and the TV page (made for big screens).
- Player of the month: a monthly award for the most points over expectation (and one for the most games), decided 3 days after the month ends, with badges on the profile, this month's race on the home page and the full standings and earlier winners on the ratings page.

## Online play (December 2026)

For the Christmas holidays, when the group is spread out: play each other on the website.

- **Challenges:** to a friend or open to anyone; with a clock (presets 3+2, 5+3, 10+5, 15+10, 30+0) or daily (1, 2, 3 or 7 days per move); your colour or random; rated or casual. Push notification when you're challenged, and for "your move" in daily games. Rematch with colours swapped.
- **The server is the arbiter:** a rules engine in Java (`chess` package, no dependencies) checks every move; perft counts on six standard positions prove the move generator. Checkmate, stalemate, threefold repetition, the fifty-move rule and insufficient material end the game by themselves.
- **The clock is kept by the server:** Fischer increment, free first moves (cancel if one doesn't come within 60 s), up to 1.5 s of network delay forgiven per move, a flag that only falls once a move made in time can't still be on its way, and out of time against a lone king is a draw.
- **A separate online rating** (Glicko-2, same rules), started from each player's over-the-board rating at their first online game (RD at least 200). Leaderboard and profile have an over-the-board / online toggle.
- **Finished rated games become ordinary confirmed games** (`mode = ONLINE`), so they show up everywhere; their moves can't be edited. Casual games don't count anywhere. Nothing is posted to Google Chat for online games.
- **Fair play:** no engine for the players during the game, as for live games.
- Polling, no WebSockets (the free API sleeps): players of a clock game poll every second, spectators every two, daily games every 15 s.

Verified by: perft (20 positions up to depth 4) and 47 rules tests (notation and disambiguation, castling rights, en passant, promotion, every drawn ending), 24 clock and ending tests, 28 API tests (challenges, races on accepting and moving, illegal and repeated moves, every ending, timeouts and late moves, cancelling, casual games, daily deadlines, the separate rating and where it starts), year-in-review tests, and 27 frontend tests (clock display, the board, the game and lobby pages).

## Later ideas

- Nynorsk as a third language
- Separate online rating pools per time control (blitz/rapid/daily), once there are enough online games
- Online games as tournament games
- Premoves and takebacks (casual games) online
- Season resets / yearly leaderboards
- More badges next to player of the month ("Giant slayer": beat someone 200+ above you)
