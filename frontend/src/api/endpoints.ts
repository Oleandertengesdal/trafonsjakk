import { api, json } from './client'
import type {
  AdminUser,
  Award,
  AwardRace,
  CommentLikes,
  CreatedInvite,
  Game,
  GameComment,
  GameContext,
  GameResult,
  GameStatus,
  GroupYear,
  Invite,
  Kudos,
  LeaderboardRow,
  LiveGame,
  Me,
  MyMatch,
  Page,
  PlayerRef,
  PlayerYear,
  Profile,
  Role,
  Stats,
  TournamentDetail,
  TournamentFormat,
  TournamentSummary,
} from './types'

export const authApi = {
  csrf: () => api<void>('/api/auth/csrf'),
  me: () => api<Me>('/api/auth/me'),
  login: (username: string, password: string) =>
    api<Me>('/api/auth/login', { method: 'POST', body: json({ username, password }) }),
  register: (body: { username: string; realName: string; password: string; inviteCode: string }) =>
    api<Me>('/api/auth/register', { method: 'POST', body: json(body) }),
  logout: () => api<void>('/api/auth/logout', { method: 'POST' }),
  checkReset: (token: string) =>
    api<{ username: string; realName: string; expiresAt: string }>('/api/auth/password-reset/check', {
      method: 'POST',
      body: json({ token }),
    }),
  resetPassword: (token: string, newPassword: string) =>
    api<Me>('/api/auth/password-reset', { method: 'POST', body: json({ token, newPassword }) }),
}

export const usersApi = {
  list: () => api<PlayerRef[]>('/api/users'),
  profile: (username: string) => api<Profile>(`/api/users/${encodeURIComponent(username)}`),
  stats: (username: string) => api<Stats>(`/api/users/${encodeURIComponent(username)}/stats`),
  updateMe: (realName: string) => api<Me>('/api/users/me', { method: 'PATCH', body: json({ realName }) }),
  changePassword: (currentPassword: string, newPassword: string) =>
    api<void>('/api/users/me/password', { method: 'POST', body: json({ currentPassword, newPassword }) }),
  /** Everything stored about you. */
  exportData: () => api<Record<string, unknown>>('/api/users/me/export'),
  deleteAccount: (password: string) => api<void>('/api/users/me/delete', { method: 'POST', body: json({ password }) }),
}

export interface NewGame {
  whiteId: number
  blackId: number
  result: GameResult
  playedAt: string
  opening?: string
  eco?: string
  notes?: string
  moves?: string
  matchId?: number
}

export const gamesApi = {
  list: (params: { player?: string; status?: GameStatus; page?: number; size?: number }) => {
    const q = new URLSearchParams()
    if (params.player) q.set('player', params.player)
    if (params.status) q.set('status', params.status)
    q.set('page', String(params.page ?? 0))
    q.set('size', String(params.size ?? 20))
    return api<Page<Game>>(`/api/games?${q}`)
  },
  pending: () => api<Game[]>('/api/games/pending'),
  get: (id: number) => api<Game>(`/api/games/${id}`),
  create: (body: NewGame) => api<Game>('/api/games', { method: 'POST', body: json(body) }),
  confirm: (id: number) => api<Game>(`/api/games/${id}/confirm`, { method: 'POST' }),
  dispute: (id: number, reason: string) =>
    api<Game>(`/api/games/${id}/dispute`, { method: 'POST', body: json({ reason }) }),
  withdraw: (id: number) => api<Game>(`/api/games/${id}/withdraw`, { method: 'POST' }),
  context: (id: number) => api<GameContext>(`/api/games/${id}/context`),
  updateMoves: (id: number, moves: string | null) =>
    api<Game>(`/api/games/${id}/moves`, { method: 'PATCH', body: json({ moves }) }),
}

export const socialApi = {
  comments: (gameId: number) => api<GameComment[]>(`/api/games/${gameId}/comments`),
  addComment: (gameId: number, body: string, parentId?: number) =>
    api<GameComment>(`/api/games/${gameId}/comments`, { method: 'POST', body: json({ body, parentId }) }),
  likeComment: (gameId: number, id: number) =>
    api<CommentLikes>(`/api/games/${gameId}/comments/${id}/like`, { method: 'PUT' }),
  unlikeComment: (gameId: number, id: number) =>
    api<CommentLikes>(`/api/games/${gameId}/comments/${id}/like`, { method: 'DELETE' }),
  editComment: (gameId: number, id: number, body: string) =>
    api<GameComment>(`/api/games/${gameId}/comments/${id}`, { method: 'PATCH', body: json({ body }) }),
  deleteComment: (gameId: number, id: number) =>
    api<void>(`/api/games/${gameId}/comments/${id}`, { method: 'DELETE' }),
  kudos: (gameId: number) => api<Kudos>(`/api/games/${gameId}/kudos`),
  giveKudos: (gameId: number) => api<Kudos>(`/api/games/${gameId}/kudos`, { method: 'PUT' }),
  takeKudos: (gameId: number) => api<Kudos>(`/api/games/${gameId}/kudos`, { method: 'DELETE' }),
}

export const leaderboardApi = {
  get: () => api<LeaderboardRow[]>('/api/leaderboard'),
}

export const awardsApi = {
  /** Standings for a month ("2026-10"); this month when left out. */
  race: (month?: string) => api<AwardRace>(`/api/awards/race${month ? `?month=${encodeURIComponent(month)}` : ''}`),
  /** Awards that were won, newest first; one person's with `player`. */
  list: (player?: string) => api<Award[]>(`/api/awards${player ? `?player=${encodeURIComponent(player)}` : ''}`),
}

export const yearApi = {
  group: (year: number) => api<GroupYear>(`/api/year/${year}`),
  player: (year: number, username: string) => api<PlayerYear>(`/api/year/${year}/players/${encodeURIComponent(username)}`),
}

export const tournamentsApi = {
  list: () => api<TournamentSummary[]>('/api/tournaments'),
  get: (id: number) => api<TournamentDetail>(`/api/tournaments/${id}`),
  create: (body: { name: string; format: TournamentFormat; doubleRoundRobin: boolean; participantIds: number[] }) =>
    api<TournamentDetail>('/api/tournaments', { method: 'POST', body: json(body) }),
  setParticipants: (id: number, participantIds: number[]) =>
    api<TournamentDetail>(`/api/tournaments/${id}/participants`, { method: 'PUT', body: json({ participantIds }) }),
  action: (id: number, action: 'join' | 'leave' | 'start' | 'cancel') =>
    api<TournamentDetail>(`/api/tournaments/${id}/${action}`, { method: 'POST' }),
  myMatches: () => api<MyMatch[]>('/api/tournaments/matches/mine'),
}

export const invitesApi = {
  list: () => api<Invite[]>('/api/invites'),
  /** Both optional. Several people (maxUses > 1) is admin-only. */
  create: (opts: { maxUses?: number; validHours?: number } = {}) =>
    api<CreatedInvite>('/api/invites', { method: 'POST', body: json(opts) }),
  revoke: (id: number) => api<void>(`/api/invites/${id}`, { method: 'DELETE' }),
}

export const adminApi = {
  disputed: () => api<Game[]>('/api/admin/games/disputed'),
  resolve: (id: number, action: 'CONFIRM' | 'VOID', result?: GameResult) =>
    api<Game>(`/api/admin/games/${id}/resolve`, { method: 'POST', body: json({ action, result }) }),
  users: () => api<AdminUser[]>('/api/admin/users'),
  updateUser: (id: number, body: { role?: Role; enabled?: boolean }) =>
    api<AdminUser>(`/api/admin/users/${id}`, { method: 'PATCH', body: json(body) }),
  passwordReset: (userId: number) =>
    api<{ token: string; expiresAt: string }>(`/api/admin/users/${userId}/password-reset`, { method: 'POST' }),
  recompute: () =>
    api<{ games: number; players: number; millis: number }>('/api/admin/ratings/recompute', { method: 'POST' }),
}

export const liveApi = {
  running: () => api<LiveGame[]>('/api/live'),
  get: (id: number) => api<LiveGame>(`/api/live/${id}`),
  start: (whiteId: number, blackId: number) => api<LiveGame>('/api/live', { method: 'POST', body: json({ whiteId, blackId }) }),
  /** Refused with `live.out_of_date` if someone saved since `version`. */
  saveMoves: (id: number, moves: string | null, version: number) =>
    api<LiveGame>(`/api/live/${id}/moves`, { method: 'PUT', body: json({ moves, version }) }),
  /** Logs the result as an ordinary game for the opponent to confirm. */
  finish: (id: number, result: GameResult) => api<Game>(`/api/live/${id}/finish`, { method: 'POST', body: json({ result }) }),
  abandon: (id: number) => api<void>(`/api/live/${id}/abandon`, { method: 'POST' }),
}
