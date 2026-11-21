// The rivalry page and a Swiss tournament's page, against mocked API answers.
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { rivalryApi, tournamentsApi, usersApi } from '../api/endpoints'
import type { Match, Rivalry, TournamentDetail } from '../api/types'
import { en } from '../i18n/en'
import { I18nProvider } from '../i18n/I18nProvider'
import { kari, ola, player } from '../test/fixtures'
import { RivalryPage } from './RivalryPage'
import { TournamentDetailPage } from './TournamentDetailPage'

vi.mock('../api/endpoints', () => ({
  rivalryApi: { get: vi.fn() },
  usersApi: { stats: vi.fn(), list: vi.fn() },
  tournamentsApi: { get: vi.fn(), action: vi.fn() },
}))
vi.mock('../auth/AuthProvider', () => ({ useAuth: () => ({ me: { id: 1, username: 'kari', realName: 'Kari Nordmann', role: 'MEMBER' } }) }))
vi.mock('../components/chess/RivalryChart', () => ({ RivalryChart: () => <div data-testid="chart" /> }))

function at(path: string, route: string, page: ReactNode) {
  localStorage.setItem('tf-lang', 'en')
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <I18nProvider>
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route path={route} element={page} />
          </Routes>
        </MemoryRouter>
      </I18nProvider>
    </QueryClientProvider>,
  )
}

describe('RivalryPage', () => {
  it('shows the score, the streaks and the upset from the first player’s side', async () => {
    const r: Rivalry = {
      a: kari,
      b: ola,
      overall: { games: 5, aWins: 3, draws: 1, bWins: 1, aPoints: 3.5 },
      aAsWhite: { games: 3, aWins: 2, draws: 1, bWins: 0, aPoints: 2.5 },
      aAsBlack: { games: 2, aWins: 1, draws: 0, bWins: 1, aPoints: 1 },
      currentStreak: { side: 'A', length: 2 },
      longestStreak: { side: 'A', length: 2 },
      biggestUpset: { gameId: 9, winner: 'B', gap: 120, playedAt: '2026-11-01T18:00:00Z' },
      bestGames: [],
      games: [],
    }
    vi.mocked(rivalryApi.get).mockResolvedValue(r)
    vi.mocked(usersApi.list).mockResolvedValue([kari, ola])
    vi.mocked(usersApi.stats).mockReturnValue(new Promise(() => {}))
    at('/rivalry/kari/ola', '/rivalry/:a/:b', <RivalryPage />)
    const v = en.rivalry
    expect(await screen.findByRole('heading', { name: v.title('Kari Nordmann', 'Ola Hansen') })).toBeInTheDocument()
    expect(screen.getByText(v.gamesPlayed(5))).toBeInTheDocument()
    expect(screen.getByRole('img', { name: v.split('Kari', 3, 1, 'Ola', 1) })).toBeInTheDocument()
    expect(screen.getAllByText(v.streak('Kari', 2))).toHaveLength(2)
    expect(screen.getByRole('link', { name: v.upset('Ola', 120) })).toHaveAttribute('href', '/games/9')
    expect(screen.getByText(v.noBestGames)).toBeInTheDocument()
    expect(rivalryApi.get).toHaveBeenCalledWith('kari', 'ola')
  })
})

describe('a Swiss tournament', () => {
  it('shows the table with Buchholz, the rounds newest first, and who sits out', async () => {
    const per = player(3, 'Per Olsen')
    const match = (id: number, round: number, white: typeof kari, black: typeof kari | null, over: Partial<Match> = {}): Match => ({
      id,
      round,
      board: 0,
      white,
      black,
      status: black ? 'COMPLETED' : 'BYE',
      result: black ? 'WHITE_WIN' : null,
      winner: white,
      games: [],
      openGameId: null,
      needsReplay: false,
      ...over,
    })
    const d: TournamentDetail = {
      summary: {
        id: 4,
        name: 'Autumn Swiss',
        format: 'SWISS',
        status: 'IN_PROGRESS',
        doubleRoundRobin: false,
        swissRounds: 3,
        participantCount: 3,
        createdBy: kari,
        winner: null,
        createdAt: '2026-11-01T18:00:00Z',
        startedAt: '2026-11-01T18:00:00Z',
        completedAt: null,
      },
      participants: [kari, ola, per].map((p, i) => ({ player: p, seed: i + 1 })),
      rounds: [
        { number: 1, name: 'Round 1', matches: [match(1, 1, kari, ola), match(2, 1, per, null)] },
        { number: 2, name: 'Round 2', matches: [match(3, 2, per, kari, { status: 'READY', result: null, winner: null }), match(4, 2, ola, null)] },
      ],
      standings: [
        { rank: 1, player: kari, played: 1, wins: 1, draws: 0, losses: 0, points: 1, sonnebornBerger: 0, byes: 0, buchholz: 1 },
        { rank: 2, player: ola, played: 1, wins: 0, draws: 0, losses: 1, points: 1, sonnebornBerger: 0, byes: 1, buchholz: 1 },
        { rank: 3, player: per, played: 0, wins: 0, draws: 0, losses: 0, points: 1, sonnebornBerger: 0, byes: 1, buchholz: 0 },
      ],
      currentRound: 2,
      totalRounds: 3,
      completedMatches: 1,
      totalMatches: 2,
      canManage: false,
      canJoin: false,
      joined: true,
    }
    vi.mocked(tournamentsApi.get).mockResolvedValue(d)
    at('/tournaments/4', '/tournaments/:id', <TournamentDetailPage />)
    const td = en.tournamentDetail
    expect(await screen.findByText(td.swissProgress(2, 3), { exact: false })).toBeInTheDocument()
    expect(screen.getByText(td.roundProgress(2, 3), { exact: false })).toBeInTheDocument()
    const table = screen.getByRole('table', { name: td.standings })
    expect(within(table).getByRole('columnheader', { name: td.buchholz })).toBeInTheDocument()
    expect(within(table).getAllByText(td.byes(1))).toHaveLength(2)
    const headings = screen.getAllByRole('heading', { level: 3 }).map((h) => h.textContent)
    expect(headings[0]).toContain('Round 2')
    expect(screen.getByText(td.bye('Ola Hansen'))).toBeInTheDocument()
    expect(screen.getByRole('link', { name: td.log })).toHaveAttribute('href', '/log?match=3')
  })
})
