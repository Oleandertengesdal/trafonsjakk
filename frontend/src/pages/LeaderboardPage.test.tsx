import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { awardsApi, leaderboardApi } from '../api/endpoints'
import type { LeaderboardRow } from '../api/types'
import { award, race } from '../components/awards/awardFixtures'
import { en } from '../i18n/en'
import { I18nProvider } from '../i18n/I18nProvider'
import { kari, ola } from '../test/fixtures'
import { LeaderboardPage } from './LeaderboardPage'

vi.mock('../api/endpoints', () => ({ leaderboardApi: { get: vi.fn() }, awardsApi: { race: vi.fn(), list: vi.fn() } }))

const row = (player: typeof kari, rank: number): LeaderboardRow => ({
  rank,
  player,
  games: 5,
  wins: 3,
  draws: 1,
  losses: 1,
  trend: 12,
  lastPlayedAt: '2026-11-01T18:00:00Z',
})

describe('LeaderboardPage', () => {
  it('marks the latest player of the month in the table, and shows the monthly standings', async () => {
    vi.mocked(leaderboardApi.get).mockResolvedValue([row(kari, 1), row(ola, 2)])
    vi.mocked(awardsApi.list).mockResolvedValue([award(ola), award(kari, { kind: 'MOST_ACTIVE' })])
    vi.mocked(awardsApi.race).mockResolvedValue(race())
    localStorage.setItem('tf-lang', 'en')
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <I18nProvider>
          <MemoryRouter>
            <LeaderboardPage />
          </MemoryRouter>
        </I18nProvider>
      </QueryClientProvider>,
    )
    const table = await screen.findByRole('table', { name: en.leaderboard.caption })
    const [, first, second] = within(table).getAllByRole('row')
    expect(await within(second).findByRole('img', { name: en.awards.reigning('October 2026') })).toBeInTheDocument()
    expect(within(first).queryByRole('img')).not.toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: en.awards.title })).toBeInTheDocument()
  })
})
