# REST API

Base path: `/api`. JSON in and out. Authentication is via the `tf_at` HttpOnly cookie set by login/register/refresh.

**Every state-changing request** (`POST`, `PUT`, `PATCH`, `DELETE`) must send the header `X-XSRF-TOKEN` with the value of the `XSRF-TOKEN` cookie. Call `GET /api/auth/csrf` once on page load to obtain it. The frontend client in `frontend/src/api/client.ts` does this automatically.

Errors use `application/problem+json` (RFC 7807) and always include a stable **`code`** that the frontend translates (Norwegian/English). Some codes carry **`params`** for the message:

```json
{ "status": 409, "detail": "Need at least 3 players to start", "code": "tournament.min_players", "params": { "min": 3 } }
```

Validation errors use `code: "validation"` with `errors` (field → English message) and `errorCodes` (field → constraint: `NotBlank`, `NotNull`, `Size`, `Pattern`, `PlainText`). `PlainText` means the text contains control characters (tabs and line breaks are fine).

The full list of codes and their Norwegian/English texts lives in `frontend/src/i18n/en.ts` / `nb.ts` under `errors.codes`. Generic codes: `conflict`, `concurrent_change`, `forbidden`, `server_error`, `server.unavailable`, `validation`, `request.malformed` (body missing, not valid JSON for the endpoint, or a bad path/query value), `request.rejected`, `not_found`, `method_not_allowed`, `not_acceptable`, `unsupported_media_type`, `request.too_large`, and from the security layer `auth.not_logged_in` (401), `forbidden` and `csrf` (403). A frontend test (`backendCodes.test.ts`) scans the backend source and fails if any code lacks a Norwegian or English text.

| Status | Meaning |
|---|---|
| 400 | Validation failed / malformed body |
| 401 | Not logged in, access token expired, or the account changed since the token was issued (disabled, or role changed). The client should call `/auth/refresh` once and retry. |
| 403 | Logged in but not allowed (`forbidden`), or missing/invalid CSRF header (`csrf`) |
| 404 | Not found |
| 409 | State conflict (e.g. game already confirmed, bracket already advanced) |
| 413 | Request body over 128 KB (`request.too_large`) |
| 429 | Too many attempts (login lockout, rate limit). `Retry-After` header is set. |
| 500 | A bug (`server_error`). The body has a **`ref`**, a six-character error code like `K7Q2MX` that is also in the server log next to the stack trace, so a report that quotes it finds the right line. Nothing else about the failure is sent. |
| 502 | Only `POST /admin/chat/test`: Google Chat didn't accept the message (`chat.failed`). |
| 503 | The database isn't answering, usually Neon waking up (`server.unavailable`). `Retry-After: 10`; try again shortly. |

Errors that happen outside the controllers (in a security filter, for example) get the same shape: `/error` answers with problem JSON too, and opening `/error` directly is a plain `404 not_found`.

```json
{ "status": 500, "title": "Internal Server Error", "detail": "Something went wrong on our side. Reference: K7Q2MX", "code": "server_error", "ref": "K7Q2MX" }
```

## Common shapes

```ts
PlayerRef   { id, username, realName, rating, rd, provisional }
GameDto     { id, white: PlayerRef, black: PlayerRef, result: "WHITE_WIN"|"BLACK_WIN"|"DRAW",
              status: "PENDING"|"CONFIRMED"|"DISPUTED"|"VOIDED", playedAt, createdAt,
              reportedBy: PlayerRef, confirmedBy: PlayerRef|null, confirmedAt, autoConfirmAt,
              opening, eco, notes, moves, disputeReason,
              tournament: { id, name, matchId, round } | null,
              ratingChange: { white: {before, after}, black: {before, after} } | null,
              canConfirm, canDispute, canWithdraw }
Page<T>     { content: T[], page, size, totalElements, totalPages }
```

## Auth

| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/auth/csrf` | – | 204, sets `XSRF-TOKEN` cookie |
| POST | `/auth/register` | `{ username, realName, password, inviteCode }` | 201 `Me`, sets cookies |
| POST | `/auth/login` | `{ username, password }` | 200 `Me`, sets cookies |
| POST | `/auth/refresh` | – (uses `tf_rt` cookie) | 204, rotates cookies |
| POST | `/auth/logout` | – | 204, clears cookies, revokes refresh token |
| GET | `/auth/me` | – | `Me` = `PlayerRef + { role, ratedGames }` |
| POST | `/auth/password-reset/check` | `{ token }` | `{ username, realName, expiresAt }`, or 400 `reset.invalid` |
| POST | `/auth/password-reset` | `{ token, newPassword }` | 200 `Me`, sets cookies. Uses up the link, ends all other sessions of that person, clears a login lockout. |

Rules: `username` `^[A-Za-z0-9_]{3,20}$` (unique, case-insensitive) · `realName` 1–60 chars · `password` 10–72 bytes.

## Users

| Method | Path | Notes |
|---|---|---|
| GET | `/users` | All active players (for pickers) → `PlayerRef[]` |
| GET | `/users/{username}` | `Profile` = `PlayerRef + { role, rank, memberSince, ratedGames, peakRating }` |
| GET | `/users/{username}/stats` | Statistics dashboard payload (see below) |
| PATCH | `/users/me` | `{ realName }` |
| POST | `/users/me/password` | `{ currentPassword, newPassword }`, signs out other sessions. Wrong guesses count toward the login lockout. |
| GET | `/users/me/export` | Everything stored about you as JSON (`Content-Disposition: attachment`): `account`, `games`, `ratingHistory`, `comments`, `commentLikes`, `kudosGiven`, `monthlyAwards`, `tournaments`, `liveGames`, `onlineGames`, `onlineChallenges`, `invitesCreated`. No password hash, and nothing others wrote. |
| POST | `/users/me/delete` | `{ password }` → 204 and logged out. Removes the person and keeps their games: the name becomes "Slettet spiller", the username `deleted_<id>`, the password can never match; comments, likes, kudos, open password links and unused invite codes go (a comment others replied to keeps its place without text); their live games stop, their online games are cancelled without a result and their challenges withdrawn. Wrong passwords count toward the login lockout (`auth.wrong_current_password`, then `auth.locked`). The only admin can't (`account.last_admin`). |

`Stats`:

```ts
{ player: Profile,
  totals: { games, wins, draws, losses, winRate, scorePct },
  white: ColorStats, black: ColorStats, favoriteColor: "WHITE"|"BLACK"|null,
  streaks: { longestWin, longestLoss, longestUnbeaten, current: { type: "WIN"|"LOSS"|"DRAW"|null, length } },
  ratingHistory: { date, rating, rd, gameId, opponent, result }[],
  peakRating, lowestRating,
  headToHead: { opponent: PlayerRef, games, wins, draws, losses, scorePct, lastPlayedAt }[],
  nemesis: { opponent, losses } | null, favoriteVictim: { opponent, wins } | null,
  bestWin: { gameId, opponent, opponentRating, playedAt } | null,
  favoriteOpening: { name, games, scorePct } | null,
  recentForm: ("W"|"D"|"L")[],            // newest first, max 10
  activity: { month: "YYYY-MM", games }[], // last 12 months
  tournamentWins }
```

## Invites

| Method | Path | Notes |
|---|---|---|
| GET | `/invites` | Your codes (admins: everyone's), without the code itself: `{ id, createdBy, createdAt, expiresAt, maxUses, useCount, uses: { player, usedAt }[], status }` |
| POST | `/invites` | Optional body `{ maxUses?, validHours? }` → `{ id, code, expiresAt, maxUses }`. **The code is only shown once.** Members: one person, at most 168 hours (default), max 5 active codes. Admins: `maxUses` 1–500 and up to 2160 hours (90 days). |
| DELETE | `/invites/{id}` | Revoke (owner or admin). People who already joined keep their accounts. |

A code is `ACTIVE` until it's used `maxUses` times (`USED`), expires or is revoked. Simultaneous sign-ups are counted one at a time, so a code never lets in more than `maxUses` people.

## Games

| Method | Path | Notes |
|---|---|---|
| GET | `/games?player=&status=&page=&size=` | Newest first. `status` defaults to all except `VOIDED`. |
| GET | `/games/pending` | Games waiting for **your** confirmation |
| GET | `/games/{id}` | |
| POST | `/games` | `{ whiteId, blackId, result, playedAt, opening?, eco?, notes?, moves?, matchId? }` → 201. You must be one of the players (or an admin). |
| POST | `/games/{id}/confirm` | Opponent (or either player if an admin logged it) |
| POST | `/games/{id}/dispute` | `{ reason }` (5–500 chars) |
| POST | `/games/{id}/withdraw` | Reporter, while `PENDING` |
| PATCH | `/games/{id}/moves` | `{ moves }` (PGN/SAN, up to 20 000 chars; blank clears). Either player or an admin, any status except `VOIDED`. Doesn't touch ratings. Not for online games (`game.online_moves`, 409): their moves are what was played. |
| GET | `/games/{id}/context` | `{ expected: { white, black }, upset, meeting, headToHead: { games, whiteWins, draws, blackWins, firstPlayedAt }, recent: { id, playedAt, whiteId, result }[] }`. See below. |

| GET | `/games/{id}/comments` | Threads, oldest first: `{ id, parentId, author, replyTo, body, createdAt, editedAt, deleted, likeCount, likedByMe, likedBy, canEdit, canDelete, canLike, replies }[]`. `replies` holds the thread's answers, oldest first (one level, like Facebook). |
| POST | `/games/{id}/comments` | `{ body, parentId? }` (1–1000 chars) → 201. Not on voided games. With `parentId` it's a reply: answering a reply files it under the same thread, and `replyTo` is the person you answered. |
| PATCH | `/games/{id}/comments/{commentId}` | `{ body }`. Author only. |
| DELETE | `/games/{id}/comments/{commentId}` | Author or admin → 204. A first comment that has replies is kept as `deleted: true` (no author, no text, no likes) so the thread still makes sense; it disappears with its last reply. |
| PUT | `/games/{id}/comments/{commentId}/like` | Like (idempotent) → `{ likeCount, likedByMe, likedBy }`. Not your own comment (`comment.own_like`). |
| DELETE | `/games/{id}/comments/{commentId}/like` | Remove your like (idempotent) → same shape |
| GET | `/games/{id}/kudos` | `{ count, givenByMe, givers: PlayerRef[] }` |
| PUT | `/games/{id}/kudos` | Give kudos (idempotent). Not on your own game (`kudos.own_game`), only once confirmed (`kudos.not_confirmed`). |
| DELETE | `/games/{id}/kudos` | Take it back (idempotent) |

Every game also carries `canEditMoves`, `commentCount`, `kudosCount`, `kudosGivenByMe`, `canGiveKudos`, `mode` (`OTB` or `ONLINE`) and `timeControl` (`"10+5"`, `"3d"`; null over the board). For an online game the players' `rating` is their online rating.

**Game context.** `expected` is each side's Glicko-2 expected score, from the ratings just before the game (the rating history) once it's confirmed, otherwise from current ratings; `black = 1 − white`. `upset` is true for a confirmed decisive game whose winner had an expected score below 0.35. `headToHead` counts the confirmed games between the two, with wins seen from this game's sides; `meeting` is this game's number among them (null if it isn't confirmed). `recent` is up to five of their other confirmed games, newest first.

Moves are checked for legality in the app (chess.js) before they're sent; the server stores them as text. Engine analysis (Stockfish) runs entirely in the browser and has no endpoint: nothing about it is sent to the server.

## Leaderboard

`GET /leaderboard?pool=` → `{ rank, player: PlayerRef, games, wins, draws, losses, lastPlayedAt, trend }[]`. `trend` is the rating change over the player's last 5 games. Provisional players are listed after ranked ones, with `rank: null`. `pool` is `OTB` (default: over the board, counting only those games) or `ONLINE` (the online rating and online games; only players who have played a rated online game). Anything else is `request.malformed`.

Profiles (`GET /users/{username}`) add `online: { rating, rd, provisional, games, peak, rank }`; statistics add `onlineRatingHistory` (the same points as `ratingHistory`, for the online rating) and `onlineStartRating` (where that line starts). `ratingHistory`, `peakRating` and `lowestRating` are over the board.

## Monthly awards ("Månedens spiller")

| Method | Path | Notes |
|---|---|---|
| GET | `/awards/race?month=` | Standings for a month (`YYYY-MM`, default this month) → `AwardRace`. Months from 2000-01 up to this month; anything else is 400 `award.bad_month`. |
| GET | `/awards?player=` | Awards that were won, newest first (player of the month, then most active, then most accurate within a month) → `Award[]`. With `player` (a username), only theirs; unknown player → 404 `player.not_found`. |

```ts
AwardRace     { month: "2026-11", status: "LIVE"|"DECIDING"|"DECIDED", endsAt, decidesAt, minGames,
                playerOfMonth: AwardCategory, mostActive: AwardCategory, mostAccurate: AccuracyCategory }
AccuracyCategory { winner: Award | null, standings: { player: PlayerRef, games, accuracy, qualified }[] }  // max 20
AwardCategory { winner: Award | null,            // set once the month is DECIDED and someone qualified
                standings: AwardStanding[] }      // everyone who played, best first (max 20)
AwardStanding { player: PlayerRef, games, wins, draws, losses, points, expected, overExpected, ratingChange, qualified }
Award         { month, kind: "PLAYER_OF_MONTH"|"MOST_ACTIVE"|"MOST_ACCURATE", player: PlayerRef, games, points,
                expected, overExpected, ratingChange,
                accuracy,        // MOST_ACCURATE only (then `games` counts analysed games); otherwise null
                decidedAt }
```

**The rules.** Only confirmed games count, and months follow Norwegian time (`app.awards.zone`, `Europe/Oslo`).

- **Player of the month** has the most points over expectation: for each game, the score (1, ½, 0) minus the expected score from Glicko-2 at both players' ratings before the game (as on the game page). Beating a stronger player is worth more; playing more helps only if you play well. Over all players the total is zero. You need at least `minGames` (3) games and a positive total. Ties: more games, then the bigger rating gain, then the older account.
- **Most active** played the most games (at least `minGames`). Ties: more points over expectation, then the older account.
- **Most accurate** has the highest average accuracy over the month's *analysed* games (see Engine analysis below), counting only games where they made at least 10 moves; at least `minGames` such games. Ties: more games, then the older account. A game analysed after its month was decided doesn't change that month's winner.

A month is `LIVE` until it ends, `DECIDING` for `app.awards.decide-after` (3 days, so late confirmations count), then `DECIDED`: the winners are stored once in `monthly_awards` and never change, even if an old game is corrected later. The standings for a decided month are still computed live. Deciding runs every hour, on startup and before every awards read, and is safe to run in parallel.

## Engine analysis

Stockfish runs in the browser. The first person to analyse a game saves the evaluation of every position here, so everyone else gets the review at once and the server can keep accuracy statistics.

| Method | Path | Notes |
|---|---|---|
| GET | `/games/{id}/analysis` | → `Analysis`. 404 `analysis.not_found` if nobody has analysed it (or its moves have changed since). |
| POST | `/games/{id}/analysis` | `{ depth, evals: Eval[] }` → `Analysis`. Saves it unless the game already has one, and returns the one that's kept (the first wins; 200 either way). `evals` needs one entry per position (the start and after every move, counted from the move text as the website reads it): 400 `analysis.wrong_length`. Each score is either `{cp}` (±100 000) or `{mate: 0–500, by: "w"|"b"}`, `best` is a UCI move or null, `depth` 1–99: otherwise 400 `analysis.invalid`. No moves: 409 `analysis.no_moves`. The server computes the accuracies itself. |
| GET | `/users/{username}/accuracy` | → `PlayerAccuracy`: confirmed, analysed games where the player made at least 10 moves. |
| GET | `/analysis/calibration` | → `Calibration`: "played like" fitted to the group's own ratings. |
| DELETE | `/admin/games/{id}/analysis` | Admin: forget a game's analysis (the next person to analyse it saves a new one) → 204. |

```ts
Eval            { score: { cp } | { mate, by: "w"|"b" },   // from White's side; only the fields that apply
                  best: "e2e4" | null }                    // the engine's move in that position
Analysis        { gameId, depth, evals: Eval[], white: { moves, accuracy }, black: { moves, accuracy }, analysedAt }
PlayerAccuracy  { games, average: number | null, best: GameAccuracy | null, recent: GameAccuracy[] }  // recent: last 30, oldest first
GameAccuracy    { gameId, accuracy, colour: "WHITE"|"BLACK", result, opponent: PlayerRef, playedAt }
Calibration     { samples, minSamples: 30, intercept: number | null, slope: number | null, low: number | null, high: number | null }
```

Accuracy is lichess's *game accuracy* (0–100), the same formula as the website. Changing a game's moves deletes its analysis. `Calibration` is a least-squares fit of each player's rating before the game on their accuracy in it, over analysed confirmed games with at least 10 of their moves; `low` and `high` are the lowest and highest rating among the samples; the website keeps an estimate between them, so a near-perfect game never reads as stronger than anyone in the group. All four stay null until there are `minSamples` samples and higher accuracy actually goes with a higher rating.

## Rivalries

| Method | Path | Notes |
|---|---|---|
| GET | `/rivalry/{a}/{b}` | Two players (usernames) against each other, from `a`'s side, confirmed games only → `Rivalry`. Unknown player → 404 `player.not_found`; the same twice → 400 `rivalry.same_player`. |

```ts
Rivalry       { a: PlayerRef, b: PlayerRef, overall: Record, aAsWhite: Record, aAsBlack: Record,
                currentStreak: Streak, longestStreak: Streak,     // wins in a row; a draw ends a streak
                biggestUpset: { gameId, winner: "A"|"B", gap, playedAt } | null,   // rating gap before the game
                bestGames: { gameId, playedAt, aAccuracy, bAccuracy, average }[], // analysed games, best 3
                games: Game[] }                                   // newest first
Record        { games, aWins, draws, bWins, aPoints }
Streak        { side: "A"|"B"|null, length }
```

The rating histories for the chart come from each player's `/users/{username}/stats`.

## Opening explorer

| Method | Path | Notes |
|---|---|---|
| GET | `/openings/games?player=` | Confirmed games that have moves or a typed opening name, newest first → `OpeningGame[]`. With `player` (a username), only theirs; unknown → 404 `player.not_found`. |

```ts
OpeningGame { id, white: PlayerRef, black: PlayerRef, result, playedAt,
              opening: string | null,   // as typed when the game was logged
              moves: string | null }    // the first 30 half-moves as SAN, space-separated (comments, numbers etc. removed)
```

The website builds the move tree and names the openings itself (chess.js and the opening list are already there).

## Push notifications

Optional: on when the server has a VAPID key pair (`PUSH_VAPID_PUBLIC_KEY`, `PUSH_VAPID_PRIVATE_KEY`). When a game is logged and waits for confirmation, the players who have to confirm it get a notification on every browser they turned notifications on in (the tag `confirm-<id>`, tapping it opens `/games/<id>`). Online play adds two: a member who is challenged (`challenge-<id>`, opens `/play`), and in daily games the player whose move it is (`move-<id>`, opens `/play/<id>`; a newer move replaces it). Clock games send none per move: they're played with the page open.

| Method | Path | Notes |
|---|---|---|
| GET | `/push/key` | `{ publicKey }`: the VAPID public key for `pushManager.subscribe`; null when push is off. |
| POST | `/push/subscriptions` | `{ endpoint, keys: { p256dh, auth } }` (the browser's `PushSubscription.toJSON()`) → 204. Adds this browser for the logged-in member (or moves it from whoever had it). Only the big push services' addresses are accepted: 400 `push.bad_endpoint`; bad keys: 400 `push.bad_keys`; push off: 409 `push.not_configured`. At most 10 per member (the oldest go). |
| POST | `/push/subscriptions/remove` | `{ endpoint }` → 204. Turns it off for this member (someone else's browser is left alone). |
| POST | `/push/status` | `{ endpoint }` → `{ subscribed }`: whether this browser gets this member's notifications. |
| POST | `/push/test` | Sends a test to the member's own browsers and waits → `{ sent }` (how many the push services accepted). 409 `push.not_subscribed` without any. |

## Year in review ("Årets sjakk")

| Method | Path | Notes |
|---|---|---|
| GET | `/year/{year}` | The group's year → `GroupYear` |
| GET | `/year/{year}/players/{username}` | One player's year → `PlayerYear`. Unknown player → 404 `player.not_found`. |

`year` is 2000 up to the current year (otherwise 400 `year.bad_year`; not a number → 400 `request.malformed`). A year runs from 1 January to 31 December in Norwegian time and only confirmed games count. `complete` is false while the year is still going. Ratings are whole numbers; `expected` is the winner's Glicko-2 expected score (0–1) before the game, from the rating history.

```ts
PlayerYear { year, complete, player: PlayerRef, games, wins, draws, losses, points,
             ratingStart, ratingEnd, ratingPeak,                 // null when games is 0
             ratingByMonth: { month: "YYYY-MM", rating }[],      // rating at the end of each month played
             bestMonth: { month, games, wins, draws, losses, overExpected, ratingChange } | null,
             biggestUpset: Upset | null,                         // the win with the lowest expected score
             rival: { opponent: PlayerRef, games, wins, draws, losses } | null,
             longestWinStreak: Streak | null, favoriteOpening: { name, games, scorePct } | null,
             busiestWeekday: { day /* 1 = Monday */, games } | null, monthsActive,
             mostKudos: BuzzGame | null, gamesWithMoves, rankByGames, players,
             awards: { month, kind: "PLAYER_OF_MONTH"|"MOST_ACTIVE" }[] }
GroupYear  { year, complete, games, players, whiteWins, draws, blackWins, gamesWithMoves, kudos, comments,
             busiestMonth: { month, games } | null, busiestWeekday: { day, games } | null,
             gameOfTheYear: BuzzGame | null,                     // most kudos + comments
             giantKiller: Upset | null,                          // the lowest-odds win of the year (below 0.5)
             awardLeaders: { player, playerOfMonth, mostActive }[],   // top 5
             mostGames: { player, games }[], climbers: { player, change, start, end }[],  // top 3
             longestWinStreak: Streak | null,
             topRivalry: { a, b, games, aWins, draws, bWins } | null }  // a has at least as many wins as b
Upset    { gameId, playedAt, winner: PlayerRef, loser: PlayerRef, expected, winnerRating, loserRating }
Streak   { player: PlayerRef, length, from, to }
BuzzGame { game: { id, white, black, result, playedAt }, kudos, comments }
```

How the highlights are chosen:

- **Best month:** the most points over expectation (as for player of the month) among months with at least two games; ties go to more games, then the bigger rating gain.
- **Rival:** the opponent played most; ties go to the closest record, then the most recent game.
- **Favourite opening:** the opening name entered most often (ignoring case and spaces); null if no openings were entered. Openings and moves are optional, so the review never depends on them.
- **Climbers:** rating gain over the year, for players with at least 3 games that year.
- **Rivalry of the year:** the pair who met most (at least twice); ties go to the closest record.

## Live games

A game being played right now. A player (or an admin) records it on a phone: every save sends the whole move text plus the `version` the phone last saw, and a save on an older version is refused, so two phones can't overwrite each other. Everyone else follows by polling. Recording moves is optional. Finishing logs an ordinary game (played when the live game started, with the moves if any) that the opponent confirms as usual. A live game nobody has saved for 6 hours is treated as abandoned.

`LiveGame`: `{ id, white, black, status: "LIVE"|"FINISHED"|"ABANDONED", moves, version, startedAt, updatedAt, gameId, canRecord }`, where `canRecord` means the caller is a player or an admin.

| Method | Path | Notes |
|---|---|---|
| GET | `/live` | Games being played now, newest first |
| POST | `/live` | `{ whiteId, blackId }` → 201. You must be one of them (or an admin: `live.not_a_player`); neither may already be in a live game (`live.already_playing`). |
| GET | `/live/{id}` | Any status (`live.not_found`) |
| PUT | `/live/{id}/moves` | `{ moves, version }` → the updated game. Players or admins; `live.out_of_date` (409) if someone saved since `version`, `live.not_running` once it has ended. Same limits as a game's moves (20 000 characters, plain text). |
| POST | `/live/{id}/finish` | `{ result }` → 201 `GameDto` (pending, for the opponent to confirm) |
| POST | `/live/{id}/abandon` | → 204. Ends it without a result. |

## Online play

Games played on the website, with the server as arbiter: it checks every move against the rules and keeps the clock. Pages poll (the free API has no WebSockets); nothing ticks on the server: a game whose time is up ends the next time anyone reads it, moves, opens the lobby, or within a minute.

**Time controls.** `LIVE`: `initialSeconds` 60–10 800 plus `incrementSeconds` 0–60 added after each move. `DAILY`: `daysPerMove` 1–14, a deadline per move that doesn't accumulate. Otherwise `online.bad_time_control`. The label is `"10+5"` or `"3d"`.

**The clock.** The first move of each side is free but must come within 60 s (daily: within the deadline), or the game is cancelled without a result (`ABORTED`). After that, thinking time is taken off the bank. A move sends `thinkMs`, the phone's own count; the server charges that, but never less than what it measured minus 1.5 s (`Clock.LAG_ALLOWANCE`), so network delay isn't charged and claiming too little saves at most 1.5 s. A flag only counts once those 1.5 s have also passed. Out of time against a lone king, or a king with one knight or bishop, is a draw (`TIMEOUT_VS_INSUFFICIENT`).

**Endings** (`endReason`): `CHECKMATE`, `STALEMATE`, `REPETITION` (threefold, automatic), `FIFTY_MOVES` (automatic), `INSUFFICIENT_MATERIAL`, `RESIGNATION`, `AGREEMENT`, `TIMEOUT`, `TIMEOUT_VS_INSUFFICIENT`. A **rated** game with a result becomes an ordinary game, already `CONFIRMED` (`mode: ONLINE`, `played_at` = when it ended), which moves the online rating and counts everywhere games do. A casual game stays only here.

| Method | Path | Notes |
|---|---|---|
| GET | `/online` | The lobby: `{ challengesToMe, myChallenges, openChallenges, myGames, myTurn, playing, recent }`. Also ends games whose time is up. |
| POST | `/online/challenges` | `{ opponentId?, colour: WHITE\|BLACK\|RANDOM, rated, kind: LIVE\|DAILY, initialSeconds?, incrementSeconds?, daysPerMove? }` → 201 `ChallengeDto`. No `opponentId`: open to anyone. `colour` is the creator's. Live challenges expire after an hour, daily after a week; at most 20 open (`online.too_many_challenges`). Not yourself (`game.same_player`). The opponent gets a push notification. |
| POST | `/online/challenges/{id}/accept` | → 201 `OnlineGameDto`. Not your own (`online.own_challenge`), not someone else's (`online.not_for_you`, 403), only while open (`online.challenge_closed`). Nobody plays two clock games at once (`online.already_playing`); daily games, as many as you like. |
| POST | `/online/challenges/{id}/decline` | The person challenged → 204 |
| POST | `/online/challenges/{id}/cancel` | The creator (or an admin) → 204 (`online.not_yours`) |
| GET | `/online/games/{id}` | `OnlineGameDto` (`online.not_found`). Anyone may watch. |
| POST | `/online/games/{id}/moves` | `{ uci: "e2e4"\|"e7e8q", ply, thinkMs? }` → the game. `ply` is the number of half-moves the board had: a stale or repeated move is `online.out_of_date`. Then `online.not_your_turn`, `online.illegal_move` (400). A move that arrives after the flag fell ends the game on time and answers `online.game_over`. |
| POST | `/online/games/{id}/resign` | Before both have moved this just cancels the game. |
| POST | `/online/games/{id}/abort` | Before both have moved (`online.cannot_abort`) |
| POST | `/online/games/{id}/offer-draw` | An offer lapses when the opponent moves; offering when the opponent already has agrees the draw. |
| POST | `/online/games/{id}/accept-draw` / `decline-draw` | `online.no_draw_offer` if there's none from the opponent |
| POST | `/online/games/{id}/rematch` | After the game: the same settings, colours swapped, as a challenge to the opponent → 201 `ChallengeDto` (`online.still_playing`) |

Every command is for the players only (`online.not_a_player`, 403) and answers `online.game_over` (409) once the game has ended.

`OnlineGameDto`: `{ id, white, black, rated, timeControl: { kind, initialSeconds, incrementSeconds, daysPerMove, label }, status: ACTIVE|FINISHED|ABORTED, result, endReason, moves, plies, lastMove, fen, turn, clock: { whiteMs, blackMs, running, toMove, serverTime, firstMoveBy, deadline }, drawOfferBy, myColour, canAbort, gameId, ratingChange, startedAt, endedAt, version }`. Times are as of `serverTime`; the page counts down from when the answer arrived. `myColour` is null for a spectator. Players carry their online rating.

## Tournaments

| Method | Path | Notes |
|---|---|---|
| GET | `/tournaments` | Summaries, newest first |
| POST | `/tournaments` | `{ name, format: "ROUND_ROBIN"|"SINGLE_ELIMINATION"|"SWISS", doubleRoundRobin?, participantIds[], rounds? }` (`doubleRoundRobin` defaults to `false` and only applies to round-robins; `rounds` (1–15) only to Swiss, default ⌈log₂ players⌉ + 1, and at most players − 1, settled when it starts) |
| GET | `/tournaments/{id}` | Detail: participants, `rounds[{ number, name, matches[] }]`, `standings` (round-robin and Swiss), `currentRound`, `totalRounds` (Swiss: the planned number), permissions. The summary has `swissRounds`. |
| PUT | `/tournaments/{id}/participants` | `{ participantIds }`. Creator/admin, during registration. |
| POST | `/tournaments/{id}/join` · `/leave` | During registration |
| POST | `/tournaments/{id}/start` | Creator/admin. Needs ≥ 2 players (≥ 3 for round-robin and Swiss). Generates all fixtures (Swiss: only the first round; each next round is paired automatically when every game of the previous one has a result). |
| POST | `/tournaments/{id}/cancel` | Creator/admin |
| GET | `/tournaments/matches/mine` | Your playable matches (`READY`, no open game), for the "Log game" picker |

`MatchDto`: `{ id, round, board, white, black, status: "WAITING"|"READY"|"COMPLETED"|"BYE", result, winner, games[], openGameId, needsReplay }`. In a Swiss tournament a `BYE` has only `white`: the player who sits out (a free point).

`StandingDto`: `{ rank, player, played, wins, draws, losses, points, sonnebornBerger, byes, buchholz }`. `byes` and `buchholz` are for Swiss (0 and null otherwise); a bye counts as a point but not as a game played.

`MyMatchDto` (`/tournaments/matches/mine`): `{ matchId, tournamentId, tournamentName, format, round, totalRounds, roundName, white, black, needsReplay }`. `roundName` is English; the UI builds a localised name from `format`, `round` and `totalRounds`. `white`/`black` are the colours for the **next** game: the pairing's colours, or after a drawn knockout game the colours of the last game reversed (`needsReplay: true`).

## Admin (`ADMIN` role)

| Method | Path | Notes |
|---|---|---|
| GET | `/admin/games/disputed` | |
| GET | `/admin/games/held` | Pending results that won't auto-confirm because a player's account is disabled or deleted. |
| POST | `/admin/games/{id}/resolve` | `{ action: "CONFIRM"|"VOID", result? }`. Works on pending, disputed and confirmed games; `result` corrects the outcome. |
| GET | `/admin/users` | |
| PATCH | `/admin/users/{id}` | `{ role?, enabled? }`. You can't demote or disable yourself, and a deleted account can't be changed (`admin.user_deleted`; `AdminUser.deleted` is true). Disabling also cancels their unused invite codes and open password links. |
| POST | `/admin/users/{id}/password-reset` | → 201 `{ token, expiresAt }`. One-time link (`/reset-password?token=…`), valid 24 h; a new link cancels the previous one. Not for yourself (`admin.cannot_change_self`) or disabled accounts (`user.disabled`). |
| POST | `/admin/ratings/recompute` | Forces a full recompute → `{ games, players, millis }` |
| GET | `/admin/chat` | `{ configured }`: whether Google Chat messages are on. The webhook link itself is never returned. |
| POST | `/admin/chat/test` | Sends a test message to the Google Chat space and waits for Google's answer → 204. `chat.not_configured` (409) without `GOOGLE_CHAT_WEBHOOK_URL`; `chat.failed` (502) if Google refused it or couldn't be reached, with `params.reason` saying why (e.g. `Google answered 403: …` with Google's own explanation; never the link). |

**Google Chat.** With `GOOGLE_CHAT_WEBHOOK_URL` set, starting a live game (`POST /live`) and logging a game (`POST /games`, or finishing a live game) also post a short message to the space, once the change is saved. A finished live game's result is a reply in the same thread as its start. Games dated more than 3 days back aren't announced. This never affects the response: sending happens afterwards, in the background (see `docs/DEPLOYMENT.md`).

## Health

`GET /actuator/health` → `{ "status": "UP" }` (public; used by Render).
