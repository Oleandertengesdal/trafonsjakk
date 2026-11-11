# REST API

Base path: `/api`. JSON in and out. Authentication is via the `tf_at` HttpOnly cookie set by login/register/refresh.

**Every state-changing request** (`POST`, `PUT`, `PATCH`, `DELETE`) must send the header `X-XSRF-TOKEN` with the value of the `XSRF-TOKEN` cookie. Call `GET /api/auth/csrf` once on page load to obtain it. The frontend client in `frontend/src/api/client.ts` does this automatically.

Errors use `application/problem+json` (RFC 7807) and always include a stable **`code`** that the frontend translates (Norwegian/English). Some codes carry **`params`** for the message:

```json
{ "status": 409, "detail": "Need at least 3 players to start", "code": "tournament.min_players", "params": { "min": 3 } }
```

Validation errors use `code: "validation"` with `errors` (field → English message) and `errorCodes` (field → constraint: `NotBlank`, `NotNull`, `Size`, `Pattern`, `PlainText`). `PlainText` means the text contains control characters (tabs and line breaks are fine).

The full list of codes and their Norwegian/English texts lives in `frontend/src/i18n/en.ts` / `nb.ts` under `errors.codes`. Generic codes: `conflict`, `concurrent_change`, `forbidden`, `server_error`, `validation`, `request.malformed` (body missing, not valid JSON for the endpoint, or a bad path/query value), `request.rejected`, `not_found`, `method_not_allowed`, `not_acceptable`, `unsupported_media_type`, `request.too_large`, and from the security layer `auth.not_logged_in` (401), `forbidden` and `csrf` (403). A frontend test (`backendCodes.test.ts`) scans the backend source and fails if any code lacks a Norwegian or English text.

| Status | Meaning |
|---|---|
| 400 | Validation failed / malformed body |
| 401 | Not logged in, access token expired, or the account changed since the token was issued (disabled, or role changed). The client should call `/auth/refresh` once and retry. |
| 403 | Logged in but not allowed (`forbidden`), or missing/invalid CSRF header (`csrf`) |
| 404 | Not found |
| 409 | State conflict (e.g. game already confirmed, bracket already advanced) |
| 429 | Too many attempts (login lockout, rate limit). `Retry-After` header is set. |

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
| GET | `/users/{username}` | `Profile` = `PlayerRef + { role, rank, memberSince, ratedGames }` |
| GET | `/users/{username}/stats` | Statistics dashboard payload (see below) |
| PATCH | `/users/me` | `{ realName }` |
| POST | `/users/me/password` | `{ currentPassword, newPassword }`, signs out other sessions |

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

**Game context.** `expected` is each side's Glicko-2 expected score, from the ratings just before the game (the rating history) once it's confirmed, otherwise from current ratings; `black = 1 − white`. `upset` is true for a confirmed decisive game whose winner had an expected score below 0.35. `headToHead` counts the confirmed games between the two, with wins seen from this game's sides; `meeting` is this game's number among them (null if it isn't confirmed). `recent` is up to five of their other confirmed games, newest first.
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

Moves are checked for legality in the app (chess.js) before they're sent; the server stores them as text.

## Leaderboard

`GET /leaderboard` → `{ rank, player: PlayerRef, games, wins, draws, losses, lastPlayedAt, trend }[]`. `trend` is the rating change over the player's last 5 games. Provisional players are listed after ranked ones, with `rank: null`.

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
| POST | `/admin/games/{id}/resolve` | `{ action: "CONFIRM"|"VOID", result? }`. Works on pending, disputed and confirmed games; `result` corrects the outcome. |
| GET | `/admin/users` | |
| PATCH | `/admin/users/{id}` | `{ role?, enabled? }`. You can't demote or disable yourself. Disabling also cancels their unused invite codes and open password links. |
| POST | `/admin/users/{id}/password-reset` | → 201 `{ token, expiresAt }`. One-time link (`/reset-password?token=…`), valid 24 h; a new link cancels the previous one. Not for yourself (`admin.cannot_change_self`) or disabled accounts (`user.disabled`). |
| POST | `/admin/ratings/recompute` | Forces a full recompute → `{ games, players, millis }` |

## Health

`GET /actuator/health` → `{ "status": "UP" }` (public; used by Render).
