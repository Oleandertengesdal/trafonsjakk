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
| GET | `/users/me/export` | Everything stored about you as JSON (`Content-Disposition: attachment`): `account`, `games`, `ratingHistory`, `comments`, `commentLikes`, `kudosGiven`, `monthlyAwards`, `tournaments`, `liveGames`, `invitesCreated`. No password hash, and nothing others wrote. |
| POST | `/users/me/delete` | `{ password }` → 204 and logged out. Removes the person and keeps their games: the name becomes "Slettet spiller", the username `deleted_<id>`, the password can never match; comments, likes, kudos, open password links and unused invite codes go (a comment others replied to keeps its place without text); their live games stop. Wrong passwords count toward the login lockout (`auth.wrong_current_password`, then `auth.locked`). The only admin can't (`account.last_admin`). |

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
| PATCH | `/games/{id}/moves` | `{ moves }` (PGN/SAN, up to 20 000 chars; blank clears). Either player or an admin, any status except `VOIDED`. Doesn't touch ratings. |
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

Every game also carries `canEditMoves`, `commentCount`, `kudosCount`, `kudosGivenByMe` and `canGiveKudos`.

**Game context.** `expected` is each side's Glicko-2 expected score, from the ratings just before the game (the rating history) once it's confirmed, otherwise from current ratings; `black = 1 − white`. `upset` is true for a confirmed decisive game whose winner had an expected score below 0.35. `headToHead` counts the confirmed games between the two, with wins seen from this game's sides; `meeting` is this game's number among them (null if it isn't confirmed). `recent` is up to five of their other confirmed games, newest first.

Moves are checked for legality in the app (chess.js) before they're sent; the server stores them as text. Engine analysis (Stockfish) runs entirely in the browser and has no endpoint: nothing about it is sent to the server.

## Leaderboard

`GET /leaderboard` → `{ rank, player: PlayerRef, games, wins, draws, losses, lastPlayedAt, trend }[]`. `trend` is the rating change over the player's last 5 games. Provisional players are listed after ranked ones, with `rank: null`.

## Monthly awards ("Månedens spiller")

| Method | Path | Notes |
|---|---|---|
| GET | `/awards/race?month=` | Standings for a month (`YYYY-MM`, default this month) → `AwardRace`. Months from 2000-01 up to this month; anything else is 400 `award.bad_month`. |
| GET | `/awards?player=` | Awards that were won, newest first (player of the month before most active within a month) → `Award[]`. With `player` (a username), only theirs; unknown player → 404 `player.not_found`. |

```ts
AwardRace     { month: "2026-11", status: "LIVE"|"DECIDING"|"DECIDED", endsAt, decidesAt, minGames,
                playerOfMonth: AwardCategory, mostActive: AwardCategory }
AwardCategory { winner: Award | null,            // set once the month is DECIDED and someone qualified
                standings: AwardStanding[] }      // everyone who played, best first (max 20)
AwardStanding { player: PlayerRef, games, wins, draws, losses, points, expected, overExpected, ratingChange, qualified }
Award         { month, kind: "PLAYER_OF_MONTH"|"MOST_ACTIVE", player: PlayerRef, games, points, expected,
                overExpected, ratingChange, decidedAt }
```

**The rules.** Only confirmed games count, and months follow Norwegian time (`app.awards.zone`, `Europe/Oslo`).

- **Player of the month** has the most points over expectation: for each game, the score (1, ½, 0) minus the expected score from Glicko-2 at both players' ratings before the game (as on the game page). Beating a stronger player is worth more; playing more helps only if you play well. Over all players the total is zero. You need at least `minGames` (3) games and a positive total. Ties: more games, then the bigger rating gain, then the older account.
- **Most active** played the most games (at least `minGames`). Ties: more points over expectation, then the older account.

A month is `LIVE` until it ends, `DECIDING` for `app.awards.decide-after` (3 days, so late confirmations count), then `DECIDED`: the winners are stored once in `monthly_awards` and never change, even if an old game is corrected later. The standings for a decided month are still computed live. Deciding runs every hour, on startup and before every awards read, and is safe to run in parallel.

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

## Tournaments

| Method | Path | Notes |
|---|---|---|
| GET | `/tournaments` | Summaries, newest first |
| POST | `/tournaments` | `{ name, format: "ROUND_ROBIN"|"SINGLE_ELIMINATION", doubleRoundRobin?, participantIds[] }` (`doubleRoundRobin` defaults to `false` and is ignored for knockouts) |
| GET | `/tournaments/{id}` | Detail: participants, `rounds[{ number, name, matches[] }]`, `standings` (round-robin), `currentRound`, permissions |
| PUT | `/tournaments/{id}/participants` | `{ participantIds }`. Creator/admin, during registration. |
| POST | `/tournaments/{id}/join` · `/leave` | During registration |
| POST | `/tournaments/{id}/start` | Creator/admin. Needs ≥ 2 players (≥ 3 for round-robin). Generates all fixtures. |
| POST | `/tournaments/{id}/cancel` | Creator/admin |
| GET | `/tournaments/matches/mine` | Your playable matches (`READY`, no open game), for the "Log game" picker |

`MatchDto`: `{ id, round, board, white, black, status: "WAITING"|"READY"|"COMPLETED"|"BYE", result, winner, games[], openGameId, needsReplay }`

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

## Health

`GET /actuator/health` → `{ "status": "UP" }` (public; used by Render).
