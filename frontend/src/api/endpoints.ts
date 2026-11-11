import { api, json } from './client'
import type {
  AdminUser,
  Game,
  GameResult,
  GameStatus,
  Invite,
  LeaderboardRow,
  Me,
  MyMatch,
  Page,
  PlayerRef,
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
}

export const usersApi = {
  list: () => api<PlayerRef[]>('/api/users'),
  profile: (username: string) => api<Profile>(`/api/users/${encodeURIComponent(username)}`),
  stats: (username: string) => api<Stats>(`/api/users/${encodeURIComponent(username)}/stats`),
  updateMe: (realName: string) => api<Me>('/api/users/me', { method: 'PATCH', body: json({ realName }) }),
  changePassword: (currentPassword: string, newPassword: string) =>
    api<void>('/api/users/me/password', { method: 'POST', body: json({ currentPassword, newPassword }) }),
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
}

export const leaderboardApi = {
  get: () => api<LeaderboardRow[]>('/api/leaderboard'),
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
  create: () => api<{ id: number; code: string; expiresAt: string }>('/api/invites', { method: 'POST' }),
  revoke: (id: number) => api<void>(`/api/invites/${id}`, { method: 'DELETE' }),
}

export const adminApi = {
  disputed: () => api<Game[]>('/api/admin/games/disputed'),
  resolve: (id: number, action: 'CONFIRM' | 'VOID', result?: GameResult) =>
    api<Game>(`/api/admin/games/${id}/resolve`, { method: 'POST', body: json({ action, result }) }),
  users: () => api<AdminUser[]>('/api/admin/users'),
  updateUser: (id: number, body: { role?: Role; enabled?: boolean }) =>
    api<AdminUser>(`/api/admin/users/${id}`, { method: 'PATCH', body: json(body) }),
  recompute: () =>
    api<{ games: number; players: number; millis: number }>('/api/admin/ratings/recompute', { method: 'POST' }),
}
