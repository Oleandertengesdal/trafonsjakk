# Trafonsjakk — Architecture

This document is the technical plan for Trafonsjakk: the database, the backend, the frontend and how they fit together. It was written before the code and is kept in sync with it.

- Product decisions and the build roadmap: [ROADMAP.md](ROADMAP.md)
- Security model: [SECURITY.md](SECURITY.md)
- REST API reference: [API.md](API.md)
- Running locally: [DEVELOPMENT.md](DEVELOPMENT.md)
- Hosting: [DEPLOYMENT.md](DEPLOYMENT.md)

---

## 1. System overview

```mermaid
flowchart LR
    subgraph Browser
        SPA[React SPA<br/>Vite + Tailwind]
    end
    subgraph Vercel
        Static[Static assets]
        Rewrite["/api/* rewrite (proxy)"]
    end
    subgraph Render
        API[Spring Boot API<br/>Java 21, Docker]
    end
    subgraph Neon
        DB[(PostgreSQL)]
    end
    subgraph Google
        Chat[Google Chat space<br/>optional]
    end
    SPA -- "HTML/JS/CSS" --> Static
    SPA -- "fetch /api/... (same origin, cookies)" --> Rewrite
    Rewrite -- HTTPS --> API
    API -- "JDBC/TLS" --> DB
    API -. "webhook (HTTPS)" .-> Chat
```

**Key choice: one origin.** In production the browser only ever talks to the Vercel domain. Vercel serves the SPA and *proxies* `/api/*` to the Spring Boot service. Because API calls are same-origin:

- auth cookies are first-party (`SameSite=Lax/Strict` works and Safari/Firefox third-party-cookie blocking doesn't apply),
- no CORS configuration is needed at all (the API rejects cross-origin browser calls by default),
- CSRF protection uses the standard double-submit cookie pattern, enforced for logged-in users too (see [SECURITY.md](SECURITY.md)).

In local development the Vite dev server does the same proxying, so dev and prod behave identically.

---

## 2. Database schema

PostgreSQL 16. The schema is owned by **Flyway** migrations in `backend/src/main/resources/db/migration`; Hibernate runs with `ddl-auto=validate` and never changes the schema.

### 2.1 ER diagram

```mermaid
erDiagram
    USERS ||--o{ GAMES : "plays white"
    USERS ||--o{ GAMES : "plays black"
    USERS ||--o{ GAMES : reports
    USERS ||--o{ RATING_HISTORY : has
    GAMES ||--|{ RATING_HISTORY : produces
    USERS ||--o{ INVITE_CODES : creates
    USERS ||--o{ REFRESH_TOKENS : owns
    USERS ||--o{ TOURNAMENTS : creates
    TOURNAMENTS ||--|{ TOURNAMENT_PARTICIPANTS : has
    USERS ||--o{ TOURNAMENT_PARTICIPANTS : joins
    TOURNAMENTS ||--|{ TOURNAMENT_MATCHES : contains
    TOURNAMENT_MATCHES ||--o{ GAMES : "decided by"
    INVITE_CODES ||--o{ INVITE_REDEMPTIONS : "used by"
    USERS ||--o| INVITE_REDEMPTIONS : "joined with"
    USERS ||--o{ PASSWORD_RESET_TOKENS : has
    GAMES ||--o{ GAME_COMMENTS : has
    GAME_COMMENTS ||--o{ GAME_COMMENTS : "replies"
    GAME_COMMENTS ||--o{ COMMENT_LIKES : has
    GAMES ||--o{ GAME_KUDOS : has
    USERS ||--o{ MONTHLY_AWARDS : wins
    GAMES ||--o| GAME_ANALYSES : "analysed in"
    USERS ||--o{ PUSH_SUBSCRIPTIONS : "gets notifications on"
    USERS ||--o{ LIVE_GAMES : plays
    LIVE_GAMES |o--o| GAMES : "becomes"
    USERS ||--o{ ONLINE_CHALLENGES : "challenges"
    ONLINE_CHALLENGES |o--o| ONLINE_GAMES : "starts"
    USERS ||--o{ ONLINE_GAMES : plays
    ONLINE_GAMES |o--o| GAMES : "becomes (rated)"

    USERS {
        bigint id PK
        varchar username "unique, case-insensitive"
        varchar real_name
        varchar password_hash "BCrypt"
        varchar role "USER | ADMIN"
        boolean enabled
        float rating "Glicko-2 r (cache)"
        float rating_deviation "Glicko-2 RD (cache)"
        float volatility "Glicko-2 sigma (cache)"
        int rated_games
        float peak_rating
        timestamptz last_game_at
        float online_rating "online pool (cache), also _deviation, _volatility, _rated_games, _peak_rating, _last_game_at"
        timestamptz deleted_at "set when the person deleted their account"
    }
    GAMES {
        bigint id PK
        bigint white_id FK
        bigint black_id FK
        varchar result "WHITE_WIN | BLACK_WIN | DRAW"
        varchar status "PENDING | CONFIRMED | DISPUTED | VOIDED"
        timestamptz played_at
        bigint reported_by FK
        bigint confirmed_by FK "null = auto-confirmed"
        varchar opening
        varchar eco
        text notes
        text moves "PGN / SAN"
        bigint tournament_match_id FK "nullable"
        varchar mode "OTB | ONLINE"
        varchar time_control "online: 10+5, 3d"
    }
    RATING_HISTORY {
        bigint id PK
        bigint game_id FK
        bigint user_id FK
        float rating_before
        float rating_after
        float rd_before
        float rd_after
        float volatility_after
        timestamptz played_at
        varchar pool "OTB | ONLINE"
    }
    INVITE_CODES {
        bigint id PK
        varchar code_hash "SHA-256, unique"
        bigint created_by FK
        timestamptz expires_at
        int max_uses "1-500"
        int use_count
        timestamptz revoked_at
    }
    INVITE_REDEMPTIONS {
        bigint id PK
        bigint invite_id FK
        bigint user_id FK "unique"
        timestamptz used_at
    }
    PASSWORD_RESET_TOKENS {
        bigint id PK
        bigint user_id FK
        varchar token_hash "SHA-256, unique"
        bigint created_by FK
        timestamptz expires_at
        timestamptz used_at
        timestamptz revoked_at
    }
    GAME_COMMENTS {
        bigint id PK
        bigint game_id FK
        bigint author_id FK
        bigint parent_id FK "thread's first comment"
        bigint reply_to_user_id FK
        varchar body "max 1000"
        timestamptz edited_at
        timestamptz deleted_at "kept for its replies"
    }
    COMMENT_LIKES {
        bigint comment_id PK,FK
        bigint user_id PK,FK
    }
    GAME_KUDOS {
        bigint game_id PK,FK
        bigint user_id PK,FK
    }
    MONTHLY_AWARDS {
        date month PK "first day"
        varchar kind PK "PLAYER_OF_MONTH | MOST_ACTIVE | MOST_ACCURATE"
        bigint user_id FK "null = nobody qualified"
        int games
        float points
        float expected
        float rating_change
        float accuracy "MOST_ACCURATE only"
        timestamptz decided_at
    }
    PUSH_SUBSCRIPTIONS {
        bigint id PK
        bigint user_id FK
        varchar endpoint UK "the browser's push service address"
        varchar p256dh "browser's public key"
        varchar auth "browser's auth secret"
        timestamptz created_at
    }
    GAME_ANALYSES {
        bigint game_id PK,FK
        char moves_hash "SHA-256 of the move text"
        smallint depth
        int plies
        text evals "JSON, one per position"
        float white_accuracy
        float black_accuracy
        bigint analysed_by FK
        timestamptz created_at
    }
    LIVE_GAMES {
        bigint id PK
        bigint white_id FK
        bigint black_id FK
        bigint started_by FK
        varchar status "LIVE | FINISHED | ABANDONED"
        text moves "nullable, saved after every move"
        bigint version "counts saves"
        bigint game_id FK "the logged game, once finished"
        timestamptz started_at
        timestamptz updated_at
    }
    ONLINE_CHALLENGES {
        bigint id PK
        bigint created_by FK
        bigint opponent_id FK "null = open to anyone"
        varchar colour "WHITE | BLACK | RANDOM (the creator's)"
        boolean rated
        varchar kind "LIVE | DAILY"
        int initial_seconds
        int increment_seconds
        int days_per_move
        varchar status "OPEN | ACCEPTED | DECLINED | CANCELLED"
        timestamptz expires_at
    }
    ONLINE_GAMES {
        bigint id PK
        bigint white_id FK
        bigint black_id FK
        boolean rated
        varchar kind "LIVE | DAILY, with the clock settings"
        varchar status "ACTIVE | FINISHED | ABORTED"
        varchar result
        varchar end_reason
        text moves "checked move by move"
        int plies
        bigint white_ms "time left when the turn started"
        bigint black_ms
        timestamptz turn_started_at
        bigint draw_offer_by FK
        bigint game_id FK "the confirmed game, if rated"
        bigint version
    }
    REFRESH_TOKENS {
        bigint id PK
        bigint user_id FK
        varchar token_hash "SHA-256, unique"
        uuid family_id
        timestamptz expires_at
        timestamptz revoked_at
    }
    TOURNAMENTS {
        bigint id PK
        varchar name
        varchar format "ROUND_ROBIN | SINGLE_ELIMINATION | SWISS"
        varchar status "REGISTRATION | IN_PROGRESS | COMPLETED | CANCELLED"
        boolean double_round_robin
        int swiss_rounds "Swiss only"
        bigint created_by FK
        bigint winner_id FK
    }
    TOURNAMENT_PARTICIPANTS {
        bigint tournament_id PK,FK
        bigint user_id PK,FK
        int seed
    }
    TOURNAMENT_MATCHES {
        bigint id PK
        bigint tournament_id FK
        int round_number
        int board_number
        bigint white_id FK "null until known"
        bigint black_id FK "null until known"
        varchar status "WAITING | READY | COMPLETED | BYE"
        varchar result "nullable"
        bigint winner_id FK
    }
```

### 2.2 Design notes

| Decision | Why |
|---|---|
| **Ratings on `users` are a cache.** The source of truth is the ordered list of `CONFIRMED` games. | Results can be confirmed late, disputed, corrected or voided. Recomputing from scratch keeps every rating and graph exactly right, no matter the order things happen in (see §3.4). |
| `rating_history` has one row per player per confirmed game | Powers the rating graph and "rating change" badges without recalculation on read. Rebuilt with the ratings. |
| Case-insensitive unique username via `UNIQUE INDEX ON users (lower(username))` | `Magnus` and `magnus` can't both exist; no `citext` extension needed (Neon/Render friendly). |
| Invite codes and refresh tokens are stored as **SHA-256 hashes** | A database leak doesn't hand out working invites or sessions. |
| `CHECK (white_id <> black_id)`, `CHECK` constraints on all enum columns | The database rejects impossible data even if a bug slips through. |
| Partial unique index: one *open* (`PENDING`/`DISPUTED`) game per tournament match | Prevents two people logging the same tournament game at once. |
| `version` columns on `games`, `tournaments`, `tournament_matches` | Optimistic locking: two simultaneous confirmations can't both win. |
| Likes and kudos have a composite primary key and are inserted with `on conflict do nothing` | One per person, so double taps can't inflate counts. |
| `monthly_awards` rows are written once and never updated | A decided month stays decided, even if an old game is corrected later (see §3.7). |
| Indexes on `(white_id, played_at)`, `(black_id, played_at)`, `(status, created_at)`, `(user_id, played_at)` on history | Profile pages, auto-confirm sweep and rating graphs are all index scans. |

The full DDL is in the Flyway migrations: [`V1__init.sql`](../backend/src/main/resources/db/migration/V1__init.sql) (core tables), `V2` (multi-use invites, password links, comments, kudos), `V3` (comment replies and likes), `V4` (monthly awards) and `V10` (online play: challenges, online games, `games.mode`, the online rating).

---

## 3. Backend (Spring Boot)

### 3.1 Stack

| Concern | Choice |
|---|---|
| Runtime | Java 21, Spring Boot 4.1 |
| Web | Spring Web MVC (REST, JSON), Bean Validation |
| Persistence | Spring Data JPA (Hibernate 7), Flyway, PostgreSQL driver |
| Security | Spring Security 7, Nimbus JOSE (HS256 signing and validation) via a cookie filter (`CookieJwtAuthenticationFilter`), BCrypt |
| Caching / rate limits | Caffeine (in-memory; single instance) |
| Tests | JUnit 6: unit tests for the algorithm classes, and API tests that run the whole app against PostgreSQL |

### 3.2 Package layout (feature-first)

```
no.trafonsjakk
├── TrafonsjakkApplication
├── config/        SecurityConfig, JwtConfig, AppProperties, SpaCsrfTokenRequestHandler,
│                  CookieJwtAuthenticationFilter, ControlCharacterFilter, ProblemWriter,
│                  RequestSizeLimitFilter, DeploymentSafetyCheck
├── common/        ApiException, GlobalExceptionHandler (RFC 7807), CurrentUser, Tokens, ClientAddress,
│                  validation/PlainText
├── auth/          AuthController, AuthService, TokenService, AuthCookies,
│                  LoginAttemptService, RefreshToken(+Repository),
│                  InviteCode, InviteRedemption (+Repositories), InviteService, InviteController,
│                  PasswordResetToken(+Repository), PasswordResetService
├── user/          User, Role, PlayerRef, UserRepository, UserService, UserController
│                  (profile and statistics endpoints), AccountService (your data, deleting)
├── live/          LiveGame(+Repository), LiveGameService, LiveGameController, LiveDtos
├── chess/         Position (rules, legal moves, SAN, FEN), ChessGame (a game and how the board ends it), Move, Color
├── online/        TimeControl, Clock, Ending (pure), OnlineChallenge, OnlineGame (+Repositories),
│                  OnlineGameService, OnlineGameController, OnlineDtos
├── game/          Game, GameResult, GameStatus, GameRepository, GameService, GameMapper,
│                  GameController, AutoConfirmService, GameContextService
├── social/        GameComment(+Repository), CommentLikeRepository, KudosRepository,
│                  SocialService, SocialController
├── rating/        Glicko2 (pure math), RatingHistory(+Repository),
│                  RatingService (recompute), LeaderboardService, LeaderboardController
├── award/         MonthlyAwardCalculator (pure), AwardRepository, AwardService, AwardController
├── review/        YearReviewCalculator (pure), YearReviewService, YearReviewController, YearReviewDtos
├── tournament/    Tournament, TournamentMatch, TournamentParticipant, enums,
│                  repositories, TournamentService, TournamentController,
│                  RoundRobinScheduler, KnockoutBracket, StandingsCalculator (pure)
├── stats/         PlayerStatsCalculator (pure), StatsService
├── notify/        ChatEvents, ChatNotifications (listener), ChatMessages (texts), GoogleChatClient
├── push/          WebPush (encryption + VAPID, JDK only), PushService, PushNotifications (listener), PushController
├── analysis/      GameAccuracy, MoveText, AnalysisService (saved reviews, profile accuracy, calibration)
└── admin/         AdminController, ChatAdminController
```

Algorithm classes (`Position`, `ChessGame`, `Clock`, `Glicko2`, `RoundRobinScheduler`, `KnockoutBracket`, `StandingsCalculator`, `PlayerStatsCalculator`, `MonthlyAwardCalculator`, `YearReviewCalculator`) are plain Java with no Spring or JPA imports. They're easy to unit test and easy to reason about.

### 3.3 Game lifecycle (opponent confirmation)

```mermaid
stateDiagram-v2
    [*] --> PENDING: a player (or admin) logs the game
    PENDING --> CONFIRMED: opponent confirms
    PENDING --> CONFIRMED: 48 h pass with no response (auto)
    PENDING --> DISPUTED: opponent disputes (with reason)
    PENDING --> VOIDED: reporter withdraws
    DISPUTED --> CONFIRMED: admin resolves (optionally corrects result)
    DISPUTED --> VOIDED: admin voids
    CONFIRMED --> VOIDED: admin voids
    CONFIRMED --> CONFIRMED: admin corrects result
    VOIDED --> [*]
```

- Only the two players (or an admin) may log a game.
- If a player logged it, **the other player** confirms. If an admin logged it on behalf of two others, **either** player may confirm.
- Only `CONFIRMED` games affect ratings, statistics and tournaments.
- **Auto-confirm:** a scheduled job runs every 10 minutes and confirms `PENDING` games older than 48 h. Free hosting sleeps when idle, so the same sweep also runs on startup and before game, leaderboard, profile, tournament and awards reads. That makes it correct even if the scheduler never fired. A result is never auto-confirmed while one of its players has a disabled or deleted account (they can no longer object): it stays pending and shows on the admin page (`GET /api/admin/games/held`) for an admin to confirm or void.

### 3.4 Rating engine: Glicko-2

**Why Glicko-2:** a friend group plays in bursts. Glicko-2 tracks a *rating deviation* (RD, how sure we are) and a *volatility* (how erratic a player is). New or inactive players move quickly; regulars are stable. It's the system used by lichess.

**Parameters**

| Parameter | Value |
|---|---|
| Initial rating / RD / volatility | 1500 / 350 / 0.06 |
| System constant τ | 0.5 |
| Convergence ε | 0.000001 |
| Rating period | **one game** (instant updates, lichess-style) |
| Inactivity | before each game, RD grows as `φ* = √(φ² + σ²·t)` where `t` = days since the player's last game ÷ 7. Capped at 350. |
| Provisional | RD > 110 → shown with a "?" and excluded from the "ranked" leaderboard |

**Update for one game** (Glickman's algorithm, applied to each player using both players' *pre-game* values):

1. Convert to the Glicko-2 scale: `μ = (r − 1500) / 173.7178`, `φ = RD / 173.7178`.
2. `g(φⱼ) = 1 / √(1 + 3φⱼ² / π²)`, `E = 1 / (1 + exp(−g(φⱼ)(μ − μⱼ)))`.
3. `v = 1 / (g² · E · (1 − E))`, `Δ = v · g · (s − E)` where `s` ∈ {1, ½, 0}.
4. Solve for the new volatility σ′ with the Illinois (regula falsi) iteration from the paper.
5. `φ* = √(φ² + σ′²)`, `φ′ = 1 / √(1/φ*² + 1/v)`, `μ′ = μ + φ′² · g · (s − E)`.
6. Convert back: `r′ = 173.7178 μ′ + 1500`, `RD′ = 173.7178 φ′`.

The implementation is verified against the worked example in Glickman's paper (1500/200/0.06 vs three opponents → 1464.06 / 151.52 / 0.05999).

**Two pools.** Over-the-board and online games (`games.mode`) are rated separately with the same rules: each game moves only its own pool, and its `rating_history` rows carry the pool. Both are rebuilt in the same replay, in order of play. Before a player's first online game their online rating is set to their over-the-board rating at that moment, with its RD (grown by inactivity) but at least 200, so it starts in the right place and still moves quickly. Everything else (statistics, head-to-head, rivalries, monthly awards, the year in review) counts both kinds of games, each with the expected score from its own pool; the rating graph, the year in review's rating line and the climbers use the over-the-board pool.

**Recompute, don't patch.** Whenever the set of confirmed games changes (confirm, auto-confirm, admin correction, void), `RatingService.recomputeAll()`:

1. takes a PostgreSQL transaction-level advisory lock, so only one recompute runs at a time,
2. resets every player to 1500/350/0.06,
3. replays all `CONFIRMED` games ordered by `(played_at, id)`,
4. rewrites `rating_history` and the cached rating columns on `users` in one transaction.

For a friend group (hundreds to a few thousand games) this takes milliseconds. It makes back-dated games, late confirmations and voids exactly correct. The replay always covers every game, but a confirmation calls `recomputeFrom(playedAt)`, which only rewrites the `rating_history` rows from that game's date onwards (earlier rows can't have changed); the admin "recompute" button still rewrites everything. Members can date a game at most `app.games.max-backdate` (30 days) back, so the rewritten part stays small; admins have no limit.

### 3.5 Tournament engine

**Creating:** any member creates a tournament, picks a format and adds participants (2–32). Members can join or leave while it's in `REGISTRATION`. The creator or an admin starts it; starting freezes the player list, assigns seeds by current rating and generates every fixture at once (Swiss: one round at a time).

**Round-robin (Berger / circle method)**

- With an odd player count, a "bye" placeholder is added; whoever is paired with it rests that round (no match row is created).
- Player 1 stays fixed and the rest rotate one place each round, giving `n − 1` rounds of `n / 2` pairings.
- Colours are assigned greedily so every player's white and black counts differ by at most one. The tests check this.
- **Double round-robin** repeats the schedule with colours swapped.
- All pairings are playable straight away: friends meet when they can, and the rounds show the suggested order. The "current round" is the lowest round with unfinished games.
- **Standings:** 1 / ½ / 0 points. Ties are broken by **Sonneborn–Berger**, then number of wins, then direct encounter (two-way ties), then name.
- The tournament completes automatically when every match is completed; the winner is first in the standings.

**Single elimination (seeded bracket)**

- The bracket size is the next power of two ≥ n. Seeds come from rating (1 = highest).
- Standard seeding order (1 v 16, 8 v 9, 5 v 12, …) built recursively, so seeds 1 and 2 can only meet in the final.
- Empty slots are **byes**, and they always go to the top seeds. A bye match is created as `BYE` and its player advances immediately.
- All rounds are created up front with `WAITING` matches. The winner of match *(round r, board b)* moves to *(r + 1, ⌊b/2⌋)*, as white if *b* is even and black if odd. A match becomes `READY` once both players are known.
- **Draws in a knockout:** the draw counts for ratings, but the match stays open and the players play again with colours reversed until there's a decisive game. The UI shows "Drawn — replay with colours reversed".
- Winning the final completes the tournament and sets the winner.

**Swiss (monrad)**

- For more players than a round-robin suits: a set number of rounds (default ⌈log₂ n⌉ + 1, at most n − 1 and 15), and only the first is paired at the start. When every game of the latest round has a result, `reevaluateMatch` pairs the next one; after the last, the tournament completes with the top of the standings as winner.
- `SwissPairing` (pure) ranks players by score, then seed. With an odd number, the lowest-ranked player who hasn't had a bye sits out (a `BYE` match with only `white`, worth a point). From the top, each player is paired within their score group with the player half a group further down ("top half against bottom half"), or the nearest one after that, never someone they've already played; when that leaves the rest unpairable it backtracks, and players float down to the next group when needed. If no pairing without a rematch exists (a late round of a long tournament), the tournament ends early.
- Colours: whoever has had White less gets White; if equal, whoever had Black last; otherwise alternating by board.
- **Standings** (`SwissStandings`): 1 / ½ / 0 and 1 for a bye; ties by **Buchholz** (the sum of the opponents' points: whoever met the stronger field ranks higher), then Sonneborn–Berger, wins and seed.
- A result changed later updates the standings; rounds already paired stay as they are, as in a real Swiss. The tests play whole Swiss tournaments of 3–16 players with random results and check: everyone plays once a round, no rematches, at most one bye each, colours balanced within two, and the table and Buchholz add up.

**Live updates:** tournament progress is event-driven. When a game linked to a match is confirmed, voided or corrected, `TournamentService.reevaluateMatch()` recomputes that match's outcome from its confirmed games and applies the difference (advance, un-advance, complete, reopen). An admin can't change a knockout result after the next round has started; the API returns `409 Conflict` instead of corrupting the bracket.

### 3.6 Statistics

`PlayerStatsCalculator` takes a player's confirmed games (chronological) plus their rating history and produces:

- current rating ± RD, peak rating, provisional flag, leaderboard rank,
- W/D/L, win rate, score % (W + ½D),
- white vs black breakdown and a **favourite colour** (better score %),
- longest win streak, longest loss streak, longest unbeaten run, current streak,
- head-to-head against each opponent (W/D/L, score, last played),
- nemesis (most losses against) and favourite victim (most wins against),
- best win (highest-rated opponent beaten, using their pre-game rating),
- most-played opening, recent form (last 10), games per month (last 12 months),
- tournament wins.

### 3.7 Monthly awards ("Månedens spiller")

Three awards per calendar month (Norwegian time), from confirmed games only:

- **Player of the month:** the most *points over expectation*: for each game, the score (1, ½, 0) minus Glicko-2's expected score at both players' pre-game ratings (taken from `rating_history`, the same numbers as the game page). The underdog who beats stronger players wins, not simply the highest-rated player, and every extra game counts if it's played well. Summed over everyone it's zero. At least 3 games and a positive total; ties go to more games, then the bigger rating gain, then the older account.
- **Most active:** the most games (at least 3); ties go to the better performance.
- **Most accurate:** the highest average accuracy over the month's analysed games (§4.4), counting games where the player made at least 10 moves; at least 3 such games. Ties go to more games, then the older account. Only games analysed by the time the month is decided count.

`MonthlyAwardCalculator` holds these rules as pure functions. `AwardService` computes standings on the fly for any month, and *decides* a month 3 days after it ends (so late confirmations count): it inserts one row per award into `monthly_awards` with `on conflict do nothing`, and never updates it. Deciding runs hourly, on startup and before every awards read (the same "free hosting sleeps" reasoning as auto-confirm), and catches up on every finished month that isn't decided yet. The zone, grace period and minimum are `app.awards.*` settings.

### 3.8 Year in review ("Årets sjakk")

`YearReviewCalculator` sums up a calendar year (Norwegian time) of confirmed games, per player and for the group: games and record, rating from the first to the last game of the year, the best month (points over expectation, at least two games), the biggest upset (the win with the lowest pre-game expected score), the rival (most games, then closest record), the longest winning streak, the favourite opening, the busiest weekday, the game with most kudos, and the year's monthly awards. The group side adds the game of the year (most kudos + comments), the giant killer, award leaders, most games, climbers and the rivalry of the year. Everything is computed on request from the games and the rating history; nothing is stored. Openings and moves are optional, so every highlight that needs them is simply left out when they're missing. See [API.md](API.md#year-in-review-årets-sjakk) for the exact rules.

### 3.9 Live games

`live_games` holds games being played right now (V5). The design is deliberately simple because the free API sleeps and has one instance: no WebSockets, just polling. The recorder saves the whole move text after every move with the version it last saw; the row is locked while saving (`findByIdForUpdate`) and an older version is refused with `live.out_of_date`, after which the phone takes the newer moves. Followers poll every 3 seconds, the TV every 4. Finishing goes through `GameService.create`, so the normal confirmation, rating and tournament rules apply unchanged. Idle games (6 hours without a save) are marked abandoned whenever live games are read.

Spectators get a live analysis view (`LiveSpectator`, loaded lazily): the board follows the game, they can step back through the moves (and jump back to the live position, with a count of the moves they missed), and they can switch on **Analyse**. The engine (the same in-browser Stockfish and the same parts as the game analysis: `useStockfish`, `AnalysisBoard`, `EnginePanel`) searches whatever position is on the board to depth 18 with three lines (MultiPV), reporting as it deepens; a new move or stepping back stops the old search and starts a new one. The page shows an evaluation bar, an arrow for the best move and the three best continuations. **Fair play:** the players (and anyone recording the game) never get this view during the game, and the TV shows no evaluation, since players can see it. This is a guard in the interface, not a lock: a player could still open the page logged in as someone else.

### 3.9a Online play

Games played on the site (`online` package), refereed by the server. **Rules:** `chess.Position` is a small dependency-free move generator (mailbox board, pseudo-legal moves filtered by "is my king attacked afterwards", castling through check refused, en passant, promotion) with SAN and FEN; `PerftTest` checks it against the published perft counts of six positions (one wrong legal move anywhere changes the count). `ChessGame` replays the stored score sheet and ends the game on checkmate, stalemate, insufficient material, threefold repetition (positions compared with castling rights, and en passant only when it can actually be played) and the fifty-move rule.

**Clock** (`Clock`, pure and immutable): stored as each side's time left when the current turn started, plus when it started; nothing ticks. Live games: thinking time comes off the bank and the increment is added; each side's first move is free but must come within 60 s, or the game is cancelled. The server measures from when it stored the opponent's move, which includes the move's way to the phone and back, so the phone sends its own thinking time; the server charges that, but never less than its own measure minus 1.5 s. A flag counts only once those 1.5 s have also gone by, so a move made in time that's still on its way is never beaten by a spectator's poll. Daily games: each move has a deadline, the days don't accumulate.

**Flow.** A challenge (to a member or open) is accepted with the row locked (one accept wins); both players are locked with an advisory lock and their old games settled before "one clock game at a time" is checked. Every command runs with the game row locked and first *settles* it (cancels or ends it on time if due), committing that even when the command itself is then refused, so a move that arrives too late loses on time. A move carries the ply it was made on, so a double tap or a retry is refused rather than played twice. Pages poll; a game whose time is up ends on the next read, the next lobby, or within a minute (scheduler). A rated game with a result is stored through `GameService.recordOnline` as a confirmed game (no confirmation needed: the server saw it) and the ratings are recomputed. No Chat messages; push notifications for challenges and daily "your move".

### 3.9b Google Chat messages (optional)

With `GOOGLE_CHAT_WEBHOOK_URL` set, the API posts to a Google Chat space through its incoming webhook. `LiveGameService.start` and `GameService.create` publish `LiveGameStarted` / `GameLogged` events; `ChatNotifications` handles them with `@TransactionalEventListener`, so a message is only sent once the change has been committed (a refused request announces nothing). The text (Norwegian, names escaped) comes from `ChatMessages`; the link to the site uses `APP_PUBLIC_URL` and is left out without it. Each message has a `threadKey`: `live-<id>` for a live game and for the game it turns into, so the result is a reply under the start, otherwise `game-<id>`. Games dated more than 3 days back aren't announced.

`GoogleChatClient` sends from one background thread with a bounded queue (100), at most one message every 1.1 s (Chat's limit is one a second per space), 5 s connect and 10 s request timeouts, and two retries with backoff for 429, 5xx and network errors. Other answers (a deleted webhook, say) drop the message with a warning. It only accepts `https://chat.googleapis.com/v1/spaces/…` links (plain `http` on localhost for tests); anything else logs an error at startup and leaves chat off. The link is a secret and is never logged. Messages with a thread key are sent with `messageReplyOption=REPLY_MESSAGE_FALLBACK_TO_NEW_THREAD`; the test message has neither. Admin → Google Chat shows whether it's on and sends a test message; if Google refuses it, the admin sees Google's status and explanation.

### 3.9c Push notifications (optional)

With a VAPID key pair in the environment, members can turn on notifications per browser (Settings → Notifications). `lib/push.ts` checks what's possible (no push in this browser; iPhone/iPad needs the home-screen app; push off on the server; blocked), asks for permission, subscribes with the server's public key and posts the subscription. `GameService.create` publishes `ConfirmationNeeded` for a pending game with the players who have to confirm it; `PushNotifications` handles it after commit and `PushService` sends from one background thread. `WebPush` does the cryptography with the JDK only: the message is encrypted for the browser (RFC 8291, `aes128gcm`: ECDH with a fresh key, HKDF, AES-128-GCM) and the request carries a VAPID JWT signed with ES256 (RFC 8292). A 404/410 from the push service deletes the subscription. Only the big push services' hosts are accepted as endpoints (no SSRF). The service worker (`public/sw.js`) shows the notification and opens the game when it's tapped (only same-origin URLs). Tests decrypt what the server sends with a pretend browser's keys and verify the VAPID signature.

### 3.10 Your data and deleting an account

`AccountService.export` collects everything stored about the person with plain SQL. `delete` (password required, same lockout as login) keeps the games and rating history, because they are the opponents' history too, and removes what identifies the person: see [API.md](API.md#users). `users.deleted_at` marks it so an admin can't switch it back on, and `deleted_` usernames can't be registered.

### 3.11 Error format

All errors are [RFC 7807](https://www.rfc-editor.org/rfc/rfc7807) `application/problem+json` with a stable `code` that the frontend translates (see [API.md](API.md)):

```json
{ "type": "about:blank", "title": "Bad Request", "status": 400, "detail": "Validation failed",
  "code": "validation", "errors": { "username": "3–20 characters: letters, digits and underscore" },
  "errorCodes": { "username": "Pattern" } }
```

Stack traces and SQL never reach the client. An unexpected error (500) instead gets a short `ref` (`ErrorRef`: six characters without 0/O or 1/I/L), returned to the client and logged with the stack trace as `Unhandled error [ref K7Q2MX] GET /api/...`. When the database can't be reached (`CannotCreateTransactionException`, `DataAccessResourceFailureException` or SQL state class 08/57P anywhere in the cause chain) the answer is `503 server.unavailable` with `Retry-After`, because it's temporary. `ProblemErrorController` replaces Spring Boot's `/error` page so errors raised outside Spring MVC have the same shape.

On the frontend:

- **404s** (`NotFoundPage`): an unknown address, or a game, player, tournament or live game the API says doesn't exist, shows the Trafon facade with its lights on and "404" on the house-number plate, and a link to the list it would have been in.
- **Crashes** (`ErrorBoundary` + `CrashPage`): one boundary around each page inside the app shell (the navigation survives, and moving to another page resets it) and one around the whole app. The facade goes dark ("Sikringen gikk": Trafon was a transformer station), with an error code made in the browser, reported to Sentry when that's on, and a "report it" link that carries the code to the feedback page and into the email.
- **New deploys**: a lazily loaded chunk that no longer exists means a new version is out, not a bug. The app reloads once by itself (`lib/staleChunks.ts`, also on Vite's `vite:preloadError`); if that doesn't help, the page says a new version is out.
- **Inline errors** (`ErrorNotice`): the message in the person's language, a retry button, and for 500s the server's error code with a copy button and a "report it" link. Loads retry while the server or database wakes up (502–504, network errors), and only once on a 500.
- **Offline**: a banner under the header while the browser is offline, and "you're offline" instead of "can't reach the server".

---

## 4. Frontend (React)

### 4.1 Stack

| Concern | Choice |
|---|---|
| Build | Vite, TypeScript (strict) |
| Styling | Tailwind CSS v4 (CSS-first `@theme` tokens) |
| Routing | React Router (data-less library mode) |
| Server state | TanStack Query (caching, background refetch, optimistic updates) |
| Charts | Recharts |
| Icons | Lucide React |
| Fonts | Self-hosted via `@fontsource` (no third-party font requests; tight CSP) |
| Languages | Norwegian bokmål (default) and English, own lightweight typed i18n (§4.6) |

### 4.2 Routes

| Path | Page | Access |
|---|---|---|
| `/login`, `/register` | Auth (register requires invite code) | public |
| `/reset-password` | Choose a new password from an admin's one-time link | public |
| `/feedback` | "Found a problem?": how to report a bug by email | public |
| `/` | Home: your rating card, in December and January the year in review, pending confirmations, tournament games to play, player of the month (last month's winners and this month's top three), latest games, top of the table, active tournaments | member |
| `/log` | Log a game (optionally pre-filled from a tournament match) | member |
| `/games` | All games, filter by player/status | member |
| `/games/:id` | Game detail: confirm / dispute / withdraw, kudos, replay with move statistics, expected score / upset / head-to-head, moves, comments with replies and likes | member |
| `/leaderboard` | Ratings table, and the monthly standings with earlier winners | member |
| `/players/:username` | Profile + statistics dashboard, award badges, link to their year in review | member |
| `/year/:year` | Year in review for the group: story cards | member |
| `/year/:year/:username` | Year in review for one player: story cards that can be shared as images | member |
| `/tournaments` | List | member |
| `/tournaments/new` | Create | member |
| `/tournaments/:id` | Standings & fixtures (RR) or bracket (KO) | member |
| `/invites` | Create / revoke invite codes | member |
| `/live` | Games being played now, start a live game, link to TV mode | member |
| `/play` | Online play: challenges to and from you, your games (your move first), the challenge form, open challenges, games to watch, your recent games | member |
| `/play/:id` | An online game: the board for the players, clocks, draw offers, resign, cancel, rematch; spectators watch. No engine. | member |
| `/live/:id` | A player records the moves on the board (saved after every move) and taps the result. Spectators follow the board, step back through the moves, and can switch on live Stockfish analysis. Keeps the screen on. | member |
| `/tv` | TV mode: full-screen, read-only scoreboard (live boards, ratings, latest results), always dark, keeps the screen on. Outside the app's navigation. | member |
| `/settings` | Real name, password, language, light/dark, download your data, delete your account | member |
| `/admin` | Disputes, users (role/enable, password links), recompute ratings | admin |

### 4.3 Component structure

```
src/
├── main.tsx, App.tsx            router + providers
├── api/                         client.ts (fetch wrapper: CSRF header, 401→refresh→retry),
│                                endpoint modules, types.ts (DTOs)
├── auth/                        AuthProvider, RequireAuth, RequireAdmin
├── components/
│   ├── brand/                   LogoMark (pawn in an arched window), TrafonFacade (building drawing)
│   ├── layout/                  AppShell (sidebar ≥ md, bottom tab bar on mobile), PageHeader
│   ├── ui/                      Button/ButtonLink, Field/Input/Select/Textarea, Panel, Skeleton,
│   │                            EmptyState, ErrorNotice, Dialog, Toast, Segmented, LanguageSwitch
│   ├── chess/                   Avatar, PlayerLink/Rating, ResultMark/RatingDelta/FormStrip, GameRow,
│   │                            GameActions, AdminResolveControls, RatingChart, ActivityBars,
│   │                            Crosstable, Bracket, PlayerSelect, MoveRecorder, GameMoves,
│   │                            GameViewer (replay + move statistics), GameContextPanel,
│   │                            GameComments, KudosButton, EvalGraph/EvalBar, AnalysisBoard, EnginePanel,
│   │                            MoveList (engine analysis, shared by the game page and live games),
│   │                            PlayBoard (the online board: tap or drag, legal squares, promotion)
│   ├── awards/                  MonthlyAwardCard (home), AwardsPanel (ratings page), AwardBadges (profile)
│   ├── year/                    StoryViewer, StoryCard, player and group slides, shareCard (card → PNG),
│   │                            YearBanner (home, December–January)
│   └── admin/                   ResetLinkButton
├── chess/                       moves.ts (chess.js: legality, PGN, move statistics), movetext.ts, explorer.ts
│                                (opening tree), openingKey.ts, openings.json (opening names, CC0),
│                                engine.ts (Stockfish over UCI in a Web Worker), analysis.ts (scores, judgements,
│                                accuracy, "played like"), useStockfish.ts (one engine per board: review,
│                                cached in this browser, then live lines)
├── pages/                       one file per route
├── i18n/                        en.ts, nb.ts (dictionaries), I18nProvider, format.ts (locale-aware
│                                dates/numbers), rounds.ts, useErrorText.ts (server error codes → text)
└── lib/                         cn.ts, theme.ts, gameResult.ts, safePath.ts, contact.ts, onlineClock.ts
                                 (the online clock as shown: counting down between polls, presets)
```

### 4.4 Engine analysis (Stockfish in the browser)

Analysis is optional and only offered for games with recorded moves. Nothing runs until someone presses **Analyse** under the board; the choice is remembered on the device (and shared with live games). Everything Stockfish does for one board goes through **one engine** (`useStockfish`), so a phone never runs two at once:

1. `Engine.load()` starts Stockfish 18 (the 7 MB "lite, single-threaded" WebAssembly build in `public/engine`, GPLv3, served unmodified from our own origin) in a **Web Worker** and does the UCI handshake. Single-threaded means no cross-origin-isolation headers are needed. The worker is terminated when analysis is switched off or the page closes.
2. **Review**, once per game: every position is evaluated to depth 14 (at most 1.5 s each); finished positions (mate, stalemate, draws) are scored without the engine. A short game takes a few seconds, a long one under a minute on a phone. **The review is shared:** before reviewing, the viewer asks the server for a saved analysis (`GET /api/games/{id}/analysis`); if there is one, it's used and the engine only does the live lines. If not, the browser reviews the game and saves the evaluations (`POST`), so nobody has to do it again. The first save wins; changing the moves deletes it; an admin can delete one from the game page. The server (`analysis` package) checks the shape (one entry per position, counted from the move text with `MoveText`, the same tokenizer as the website), computes both players' accuracy with `GameAccuracy` (a Java port of the formula below, checked against the website's numbers in a test), and uses it for profile statistics (average accuracy, best-played game), the *most accurate* award and the "played like" calibration. The review is also kept in `localStorage`, keyed by a hash of the moves and the depth, so it still works when the server can't be reached.
3. `reviewGame()` turns the scores into win percentages (lichess's curve) and judges each move by how much winning chance it gave away: 10 points is an inaccuracy (?!), 20 a mistake (?), 30 a blunder (??). Allowing a mate is always a blunder; throwing away a forced mate is a mistake unless the position is still easily won. The engine's own move is never marked. Accuracy per move uses lichess's formula, and a player's accuracy for the game is lichess's *game accuracy*: the average of a weighted mean and a harmonic mean of their move accuracies. Each move is weighted by how much White's winning chance swings in a window of positions around it (standard deviation, 0.5–12), so moves in a sharp position count for more than moves in a quiet or already decided one, and a long, easy, won endgame doesn't hide the blunders; the harmonic mean makes a few terrible moves weigh heavily. `playedLike()` turns a player's accuracy into a rough rating ("≈1550", shown under the accuracy), only for a player with at least 10 moves: the rating that players who play one game that accurately usually have. The table behind it was **measured**: 432 rated lichess games (864 player-games, players rated 800–2300, about as many at every level) were analysed exactly as the site does it (the same Stockfish build in Node, depth 14, `reviewGame()`), and for each accuracy the average rating of the players who reached it was taken, smoothed and kept rising. That is the right direction for an estimate: one game's accuracy explains only about a fifth of the differences in rating (correlation ≈ 0.47), so the estimate stays near the middle, from 1200 for a very poor game to 1850 for a flawless one, and is still typically ±300 off. (The first version read the rule-of-thumb accuracy for a rating the other way round, extrapolating past 95 %, so an easy win over a collapsing opponent, where both players often get 93–97 %, came out as 2400–2600.) The same run checked the accuracy itself: depth 8, 14 and 18 give nearly the same numbers (depth 18 is 0.4 points lower on average), and variations of the formula (leaving out opening-book moves or moves in already decided positions, a flatter win-chance curve, average centipawn loss) didn't track rating meaningfully better, so the accuracy stays lichess's, comparable with what players see there. Once the group has 30 analysed player-games, `GET /api/analysis/calibration` fits a straight line from accuracy to the players' own ratings before the game, and "played like" uses that instead: a rating on the group's scale ("how players here who play this accurately are usually rated"), which the page says, kept between the lowest and highest rating in the fit.
4. **Live lines**, after the review: the same engine thinks about the position on the board to depth 18 with its three best lines (MultiPV), reporting as it looks deeper. `Engine` runs one job at a time (`enqueue`): a new position stops the old search first, and a review position goes back to one line.
5. With analysis on, the viewer shows the evaluation bar, a graph of the game (tap to jump), the marks in the move list, one sentence about the move just played ("Feil: kostet 3,3 bønder. Bedre var 5. Bxf7+."), the engine panel with its three lines and the depth, and accuracy per player. With it off, the page is just the board, the moves and a few numbers about the game.
6. **Your own moves:** with analysis on you can drag a piece, tap a piece and then a square, or tap one of the engine's lines. The game's own next move just steps forward; anything else starts *your own line* branching off the game, which the engine follows. "Previous move" takes back your last move; the ✕ (or stepping through the game) goes back to the game.

Keeping it smooth: the board is a memoized component that only redraws when the position, the last move, the arrow or the selection changes, not on every engine update; the engine panel updates at most every 300 ms and only when the lines actually changed; a search starts 250 ms after the position settles (so stepping quickly through moves doesn't start one per move); the arrow appears from depth 8, when the engine's first choice has stopped jumping around; and the engine rests while the page is hidden. Placeholders keep the panel's height while the engine starts. Measured on a throttled phone profile (6× slower CPU) while stepping through 20 moves: one worker instead of two, and less than half the main-thread blocking of the previous version.

The CSP allows this with `script-src 'self' 'wasm-unsafe-eval'` and `worker-src 'self'`; JavaScript `eval` stays forbidden.

### 4.4a Opening explorer

`/openings` (`OpeningsPage`, loaded lazily) fetches the confirmed games' first 30 half-moves (`GET /api/openings/games`) and builds the explorer in the browser (`chess/explorer.ts`): every game is replayed with chess.js, and each position is keyed by `positionKey()` (a hash of the FEN's pieces, side to move, castling and en passant), so the same position reached by another move order is merged, as on lichess. Each position has the moves played from it with White wins / draws / Black wins and the score (for one player: their wins, draws, losses and score, optionally only as White or Black), and the games that went through it. Openings are named from lichess's CC0 opening list, turned into `chess/openings.json` (≈3 900 positions, ≈50 kB gzipped, loaded as a hashed file only on this page) by `scripts/build-openings.mjs`; a game is named by the last named position it reaches and grouped by family ("Sicilian Defense"), and games without moves count under the name typed when they were logged. The filters and the line are in the URL, so a position can be shared.

### 4.4a2 Rivalry pages

`/rivalry/:a/:b` (`RivalryPage`) shows two players against each other. `GET /api/rivalry/{a}/{b}` (`rivalry` package) loads their confirmed games and `RivalryCalculator` (pure) works out the record overall and by colour, the current and longest winning streak (a draw ends one), the biggest upset (the largest pre-game rating gap the lower-rated player overcame, from `rating_history`) and the three best-played games (highest average accuracy of both players, from saved analyses). The page draws both rating histories on one chart (`RivalryChart`: two stepped lines, a legend and a shared tooltip) from the players' existing stats, and lists every game. Profiles' head-to-head rows and the game page's head-to-head section link here; a picker switches the opponent.

### 4.4b Installable app (PWA)

`public/manifest.webmanifest` (standalone, icons incl. a maskable one, shortcuts to Live, Log game and TV) and the Apple meta tags in `index.html` let people add Trafonsjakk to the home screen of a phone or tablet, where it opens without the browser bar. `public/sw.js` is deliberately minimal: it caches nothing but an offline page, so every deploy shows up immediately. It also shows push notifications (§3.9c). It's registered only in production builds. `useWakeLock` keeps the screen on during a live game and on the TV (Screen Wake Lock API; quietly does nothing where unsupported).

### 4.4c Error alerts (optional)

With `VITE_SENTRY_DSN` set at build time, `lib/errorReporting.ts` loads Sentry (in its own chunk, only then) and reports crashes in the browser plus any `500` from the API, since the API itself has no error alerts. No user data, IP, cookies, headers, bodies or URL query strings are sent (reset tokens and invite codes travel in query strings). Without the variable nothing is loaded.

### 4.5 Visual language: the Trafon facade

The look is drawn from the Trafon building (a red-painted two-storey facade with cream window trim, arched windows and a slate roof), without any reference to the organisations that use it.

| Token | Light | Dark | Used for |
|---|---|---|---|
| `page` / `surface` | `#F4EEE3` / `#FFFCF7` (window-trim cream) | `#17181B` / `#202226` | backgrounds |
| `ink` | `#23252A` (roof slate) | `#F2EBDF` | text |
| `brand` | `#C23A2E` (Trafon red) | `#C43C2F` | **fills** behind cream text: buttons, the brick hero, logo, selected states |
| `accent` | `#B8352A` | `#EF6A5A` | red **text**, links, chart line, focus ring |
| `win` / `loss` / `draw` | green / brick / slate | lighter variants | results, rating changes |
| `gold` | `#8A6416` | `#D8AF5A` | "waiting for confirmation", upsets and monthly awards |

The year-in-review story cards use the facade's colours as **fixed** values (brick, slate roof, window-trim cream, brass, the green door) rather than theme tokens, so a card looks the same in light and dark mode and in the shared image. Each card is shaped like the building's arched windows, and its big number is sized in container units so it scales with the card.

`brand` and `accent` are split so red text stays readable on dark backgrounds while red buttons keep enough contrast for cream labels. Every text/background pair was measured to be at least 4.5:1.

- **One bold element:** the home rating card is a red "facade" with painted brick courses and a line drawing of the building. The champion banner reuses it. Everything else is quiet cream panels.
- **Arched windows** recur as a motif: avatars (initials in an arched window), the logo (a pawn in an arch), the tops of the hero and game cards.
- **Illustration:** `TrafonFacade` is a hand-drawn SVG of the building, in full colour on the login page and as a single-colour outline on red surfaces.
- **Type:** Bricolage Grotesque for headings and big numbers, IBM Plex Sans for UI text, tabular figures for every rating and score. Results always use scoresheet notation (1–0, ½–½, 0–1), which is the same in both languages.
- **Motion:** 150–200 ms transitions, one rising-number animation on the rating, skeleton loaders, and `prefers-reduced-motion` respected.
- **Themes:** light by default; dark is a separately tuned evening palette (Settings → Appearance), remembered per device.

### 4.6 Languages (i18n)

- `i18n/en.ts` defines every UI string; `i18n/nb.ts` is typed as `Dict = typeof en`, so **the build fails if Norwegian is missing a key or has a different function signature**. Strings that need values are functions (`games: (n) => …`) and handle plurals.
- `useI18n()` returns `t` (the dictionary for the current language), `f` (locale-aware formatters: `nb-NO`/`en-GB` dates, relative times like "for 4 timer siden", `60 %` vs `60%`) and `setLang`.
- **Default is Norwegian (bokmål)**; the choice is stored in `localStorage` and sets `<html lang>`. The switch is in the sidebar, the mobile account menu, the login/register pages and Settings.
- **Server errors are translated too.** Every API error carries a stable `code` (e.g. `invite.invalid`) and optional `params`; `useErrorText()` maps it to the current language, falling back to a generic message. Validation errors include per-field constraint codes (`NotBlank`, `Size`, …).
- Round names (Finale/Final, Semifinaler/Semifinals) are computed client-side from the round number and format.
- **Guard rails:** `npm run check:i18n` (run in CI) deep-compares the `en` and `nb` keys, including the error-code tables, and scans every component for hard-coded user-facing text (JSX text, `aria-label`/`title`/`placeholder`/`label`/`hint` attributes, toast messages).

**Adding a string:** add the key to `en.ts`, then to `nb.ts` (TypeScript will tell you if you forget), and use it as `t.section.key`.

---

## 5. Request flow examples

**Log → confirm → ratings → tournament**

```mermaid
sequenceDiagram
    participant A as Alice (SPA)
    participant API
    participant DB
    participant B as Bob (SPA)
    A->>API: POST /api/games {white: alice, black: bob, result: WHITE_WIN, matchId: 12}
    API->>DB: insert game (PENDING)
    API-->>A: 201 Created
    B->>API: GET /api/games/pending
    API-->>B: [game #88 waiting for you]
    B->>API: POST /api/games/88/confirm
    API->>DB: status=CONFIRMED
    API->>API: RatingService.recomputeAll() (advisory lock)
    API->>API: TournamentService.reevaluateMatch(12) → winner advances
    API-->>B: 200 {game, ratingChange}
```
