import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { awardsApi } from '../../api/endpoints'
import type { AwardRace } from '../../api/types'
import { en } from '../../i18n/en'
import { makeFormatters } from '../../i18n/format'
import { I18nProvider } from '../../i18n/I18nProvider'
import { kari, ola, player } from '../../test/fixtures'
import { award, race, standing } from './awardFixtures'
import { AwardBadges } from './AwardBadges'
import { AwardsPanel } from './AwardsPanel'
import { MonthlyAwardCard } from './MonthlyAwardCard'

vi.mock('../../api/endpoints', () => ({ awardsApi: { race: vi.fn(), list: vi.fn() } }))

const a = en.awards
const per = player(3, 'Per Olsen')

function wrap(ui: ReactNode) {
  localStorage.setItem('tf-lang', 'en')
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <I18nProvider>
        <MemoryRouter>{ui}</MemoryRouter>
      </I18nProvider>
    </QueryClientProvider>,
  )
}

/** race(undefined) is this month (November); race('2026-10') is October. */
function races(current: AwardRace, previous: AwardRace = race({ month: '2026-10', status: 'DECIDED' })) {
  vi.mocked(awardsApi.race).mockImplementation(async (month?: string) => (month === previous.month ? previous : month ? race({ month }) : current))
}

beforeEach(() => {
  vi.mocked(awardsApi.race).mockReset()
  vi.mocked(awardsApi.list).mockReset()
})

describe('MonthlyAwardCard (home page)', () => {
  it("shows last month's winners and this month's top three", async () => {
    races(
      race({
        playerOfMonth: {
          winner: null,
          standings: [
            standing(per, { overExpected: 1.24 }),
            standing(kari, { overExpected: 0.5 }),
            standing(ola, { overExpected: -0.2 }),
            standing(player(4, 'Ingrid Berg'), { overExpected: -1.5 }),
          ],
        },
      }),
      race({
        month: '2026-10',
        status: 'DECIDED',
        playerOfMonth: { winner: award(ola), standings: [standing(ola)] },
        mostActive: { winner: award(kari, { kind: 'MOST_ACTIVE', games: 9 }), standings: [standing(kari)] },
      }),
    )
    wrap(<MonthlyAwardCard />)

    const last = (await screen.findByRole('heading', { name: a.inMonth('October') })).closest('section')!
    expect(within(last).getByRole('link', { name: 'Ola Hansen' })).toHaveAttribute('href', '/players/ola_hansen')
    expect(within(last).getByText(a.over('+2.3'), { exact: false })).toBeInTheDocument()
    expect(within(last).getByText(a.mostActiveLine('Kari Nordmann', en.common.games(9)))).toBeInTheDocument()

    const now = screen.getByRole('heading', { name: a.soFar('November') }).closest('section')!
    const rows = within(within(now).getByRole('list')).getAllByRole('listitem')
    expect(rows).toHaveLength(3)
    expect(rows[0]).toHaveTextContent('Per Olsen')
    expect(rows[0]).toHaveTextContent('+1.2')
    expect(rows[2]).toHaveTextContent('−0.2')
    expect(screen.getByText(a.shortRule(3))).toBeInTheDocument()
    expect(screen.getByRole('link', { name: a.seeStandings })).toHaveAttribute('href', '/leaderboard#awards')
  })

  it('shows who leads while last month is being decided', async () => {
    races(
      race(),
      race({ month: '2026-10', status: 'DECIDING', playerOfMonth: { winner: null, standings: [standing(kari), standing(ola)] } }),
    )
    wrap(<MonthlyAwardCard />)
    const last = (await screen.findByRole('heading', { name: a.inMonth('October') })).closest('section')!
    expect(within(last).getByText(a.leading)).toBeInTheDocument()
    expect(within(last).getByText('Kari Nordmann')).toBeInTheDocument()
    expect(within(last).getByText(a.decides(makeFormatters('en').date('2026-12-03T23:00:00Z')))).toBeInTheDocument()
  })

  it('says so when nobody qualified last month, and when nobody has played yet this month', async () => {
    races(race(), race({ month: '2026-10', status: 'DECIDED', playerOfMonth: { winner: null, standings: [standing(kari, { qualified: false, games: 2 })] } }))
    wrap(<MonthlyAwardCard />)
    expect(await screen.findByText(a.noWinner)).toBeInTheDocument()
    expect(screen.getByText(a.noGamesYet)).toBeInTheDocument()
  })

  it('leaves out last month when nobody played then', async () => {
    races(race({ playerOfMonth: { winner: null, standings: [standing(kari)] } }))
    wrap(<MonthlyAwardCard />)
    await screen.findByText('Kari Nordmann')
    expect(screen.queryByRole('heading', { name: a.inMonth('October') })).not.toBeInTheDocument()
  })

  it('shows players short of the minimum as not yet in the running', async () => {
    races(race({ playerOfMonth: { winner: null, standings: [standing(kari), standing(ola, { games: 2, qualified: false, overExpected: 1.8 })] } }))
    wrap(<MonthlyAwardCard />)
    const row = (await screen.findByText('Ola Hansen')).closest('li')!
    expect(row).toHaveTextContent(a.gamesOf(2, 3))
    expect(row).toHaveAttribute('title', a.notQualified(3))
  })
})

describe('AwardBadges (profile)', () => {
  it("lists the player's awards with month and year", async () => {
    vi.mocked(awardsApi.list).mockResolvedValue([
      award(kari),
      award(kari, { kind: 'MOST_ACTIVE', month: '2026-09', games: 11 }),
    ])
    wrap(<AwardBadges username="kari_nordmann" />)
    const list = await screen.findByRole('list', { name: a.title })
    const items = within(list).getAllByRole('listitem')
    expect(items).toHaveLength(2)
    expect(items[0]).toHaveTextContent(a.kind.PLAYER_OF_MONTH)
    expect(items[0]).toHaveTextContent('October 2026')
    expect(items[0]).toHaveAttribute('title', a.badgeDetail('+2.3', en.common.games(7)))
    expect(items[1]).toHaveTextContent(a.kind.MOST_ACTIVE)
    expect(items[1]).toHaveTextContent('September 2026')
    expect(awardsApi.list).toHaveBeenCalledWith('kari_nordmann')
  })

  it('shows nothing for someone without awards', async () => {
    vi.mocked(awardsApi.list).mockResolvedValue([])
    const { container } = wrap(<AwardBadges username="ola_hansen" />)
    await vi.waitFor(() => expect(awardsApi.list).toHaveBeenCalled())
    expect(container).toBeEmptyDOMElement()
  })
})

describe('AwardsPanel (ratings page)', () => {
  it('shows the full standings with the numbers behind them', async () => {
    races(race({ playerOfMonth: { winner: null, standings: [standing(per, { overExpected: 1.24, points: 3, expected: 1.76, ratingChange: 38 }), standing(ola, { games: 1, qualified: false })] } }))
    vi.mocked(awardsApi.list).mockResolvedValue([])
    wrap(<AwardsPanel />)
    const table = await screen.findByRole('table', { name: a.standingsCaption('November 2026') })
    const rows = within(table).getAllByRole('row').slice(1)
    expect(rows).toHaveLength(2)
    expect(rows[0]).toHaveTextContent('Per Olsen')
    expect(rows[0]).toHaveTextContent('+1.2')
    expect(rows[0]).toHaveTextContent('+38')
    expect(within(rows[0]).getByText('1.8')).toBeInTheDocument() // 3 − 1.76 shows as +1.2
    expect(rows[1]).toHaveTextContent(a.gamesOf(1, 3))
    expect(screen.getByText(a.rule(3))).toBeInTheDocument()
    expect(await screen.findByText(a.noHistory)).toBeInTheDocument()
  })

  it('steps back to earlier months and marks the winner', async () => {
    races(
      race(),
      race({ month: '2026-10', status: 'DECIDED', playerOfMonth: { winner: award(ola), standings: [standing(ola), standing(kari)] } }),
    )
    vi.mocked(awardsApi.list).mockResolvedValue([])
    wrap(<AwardsPanel />)
    const next = await screen.findByRole('button', { name: en.awardsNav.next })
    expect(next).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: en.awardsNav.previous }))
    const table = await screen.findByRole('table', { name: a.standingsCaption('October 2026') })
    const first = within(table).getAllByRole('row')[1]
    expect(within(first).getByRole('img', { name: a.kind.PLAYER_OF_MONTH })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: en.awardsNav.next })).toBeEnabled()
  })

  it('lists earlier winners by month', async () => {
    races(race())
    vi.mocked(awardsApi.list).mockResolvedValue([
      award(ola),
      award(kari, { kind: 'MOST_ACTIVE' }),
      award(per, { month: '2026-09' }),
    ])
    wrap(<AwardsPanel />)
    const history = await screen.findByRole('list', { name: a.history })
    const months = within(history).getAllByRole('listitem', { name: (n) => /2026/.test(n) })
    expect(months).toHaveLength(2)
    expect(months[0]).toHaveTextContent('October 2026')
    expect(months[0]).toHaveTextContent('Ola Hansen')
    expect(months[0]).toHaveTextContent('Kari Nordmann')
    expect(months[1]).toHaveTextContent('Per Olsen')
  })
})
