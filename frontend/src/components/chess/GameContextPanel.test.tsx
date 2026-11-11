import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { gamesApi } from '../../api/endpoints'
import type { GameContext } from '../../api/types'
import { en } from '../../i18n/en'
import { I18nProvider } from '../../i18n/I18nProvider'
import { kari, ola, pendingGame } from '../../test/fixtures'
import { GameContextPanel } from './GameContextPanel'

vi.mock('../../api/endpoints', () => ({ gamesApi: { context: vi.fn() } }))

const c = en.context

function renderPanel(ctx: GameContext, over = {}) {
  vi.mocked(gamesApi.context).mockResolvedValue(ctx)
  localStorage.setItem('tf-lang', 'en')
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <I18nProvider>
        <MemoryRouter>
          <GameContextPanel game={pendingGame({ status: 'CONFIRMED', white: kari, black: ola, result: 'BLACK_WIN', ...over })} />
        </MemoryRouter>
      </I18nProvider>
    </QueryClientProvider>,
  )
}

const base: GameContext = {
  expected: { white: 0.72, black: 0.28 },
  upset: true,
  meeting: 4,
  headToHead: { games: 4, whiteWins: 2, draws: 1, blackWins: 1, firstPlayedAt: '2026-10-01T18:00:00Z' },
  recent: [
    { id: 7, playedAt: '2026-11-01T18:00:00Z', whiteId: 1, result: 'DRAW' },
    { id: 5, playedAt: '2026-10-20T18:00:00Z', whiteId: 2, result: 'BLACK_WIN' },
  ],
}

describe('GameContextPanel', () => {
  it('shows the expected score as a split bar', async () => {
    renderPanel(base)
    const bar = await screen.findByRole('img', { name: `${c.expected}: Kari Nordmann 72%, Ola Hansen 28%` })
    expect(bar).toBeInTheDocument()
  })

  it('calls out an upset', async () => {
    renderPanel(base)
    expect(await screen.findByText(c.upset)).toBeInTheDocument()
    expect(screen.getByText(c.upsetBody('Ola Hansen', '28%'))).toBeInTheDocument()
  })

  it('shows the head-to-head from this game’s sides and earlier games', async () => {
    renderPanel(base)
    const h2h = (await screen.findByRole('heading', { name: c.headToHead })).closest('section')!
    expect(within(h2h).getByText(c.record(2, 1))).toBeInTheDocument()
    expect(within(h2h).getByText(c.draws(1))).toBeInTheDocument()
    expect(within(h2h).getByText(c.meeting(4))).toBeInTheDocument()
    const earlier = within(h2h).getByRole('list', { name: c.recent })
    const links = within(earlier).getAllByRole('link')
    expect(links).toHaveLength(2)
    expect(links[0]).toHaveAttribute('href', '/games/7')
    expect(links[0]).toHaveTextContent(c.drawn)
    // game 5: Ola (id 2) had white and black won, so Kari won
    expect(links[1]).toHaveTextContent(c.won('Kari Nordmann'))
  })

  it('says when it is their first game', async () => {
    renderPanel({ ...base, upset: false, meeting: 1, headToHead: { games: 1, whiteWins: 0, draws: 0, blackWins: 1, firstPlayedAt: '2026-11-01T18:00:00Z' }, recent: [] })
    expect(await screen.findByText(c.firstMeeting)).toBeInTheDocument()
    expect(screen.queryByText(c.upset)).not.toBeInTheDocument()
    expect(screen.queryByRole('list', { name: c.recent })).not.toBeInTheDocument()
  })
})
