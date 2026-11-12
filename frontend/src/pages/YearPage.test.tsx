import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { yearApi } from '../api/endpoints'
import type { GroupYear, PlayerYear } from '../api/types'
import { shareCard } from '../components/year/shareCard'
import { YearBanner } from '../components/year/YearBanner'
import { en } from '../i18n/en'
import { I18nProvider } from '../i18n/I18nProvider'
import { kari, ola, player } from '../test/fixtures'
import { ToastProvider } from '../components/ui/Toast'
import { YearPage } from './YearPage'

vi.mock('../api/endpoints', () => ({ yearApi: { player: vi.fn(), group: vi.fn() } }))
vi.mock('../auth/AuthProvider', () => ({ useAuth: () => ({ me: { id: 1, username: 'kari_nordmann', realName: 'Kari Nordmann', role: 'USER' } }) }))
vi.mock('../components/year/shareCard', () => ({ shareCard: vi.fn() }))

const y = en.year
const per = player(3, 'Per Olsen')

const kariYear = (over: Partial<PlayerYear> = {}): PlayerYear => ({
  year: 2026,
  complete: true,
  player: kari,
  games: 24,
  wins: 14,
  draws: 4,
  losses: 6,
  points: 16,
  ratingStart: 1500,
  ratingEnd: 1584,
  ratingPeak: 1612,
  ratingByMonth: [
    { month: '2026-03', rating: 1530 },
    { month: '2026-04', rating: 1584 },
  ],
  bestMonth: { month: '2026-03', games: 6, wins: 5, draws: 1, losses: 0, overExpected: 1.94, ratingChange: 61 },
  biggestUpset: { gameId: 9, playedAt: '2026-04-02T17:00:00Z', winner: kari, loser: ola, expected: 0.18, winnerRating: 1480, loserRating: 1650 },
  rival: { opponent: ola, games: 9, wins: 5, draws: 1, losses: 3 },
  longestWinStreak: { player: kari, length: 7, from: '2026-03-01T17:00:00Z', to: '2026-03-28T17:00:00Z' },
  favoriteOpening: null,
  busiestWeekday: { day: 2, games: 11 },
  monthsActive: 2,
  mostKudos: null,
  gamesWithMoves: 0,
  rankByGames: 1,
  players: 5,
  awards: [{ month: '2026-03', kind: 'PLAYER_OF_MONTH' }],
  ...over,
})

const groupYear = (over: Partial<GroupYear> = {}): GroupYear => ({
  year: 2026,
  complete: false,
  games: 80,
  players: 6,
  whiteWins: 40,
  draws: 10,
  blackWins: 30,
  gamesWithMoves: 12,
  kudos: 33,
  comments: 21,
  busiestMonth: { month: '2026-10', games: 14 },
  busiestWeekday: { day: 2, games: 30 },
  gameOfTheYear: { game: { id: 5, white: kari, black: ola, result: 'BLACK_WIN', playedAt: '2026-10-01T18:00:00Z' }, kudos: 6, comments: 9 },
  giantKiller: { gameId: 7, playedAt: '2026-05-01T18:00:00Z', winner: per, loser: kari, expected: 0.09, winnerRating: 1290, loserRating: 1700 },
  awardLeaders: [{ player: ola, playerOfMonth: 3, mostActive: 1 }, { player: kari, playerOfMonth: 2, mostActive: 0 }],
  mostGames: [{ player: kari, games: 30 }, { player: ola, games: 28 }],
  climbers: [{ player: per, change: 140, start: 1300, end: 1440 }],
  longestWinStreak: { player: kari, length: 7, from: '2026-03-01T17:00:00Z', to: '2026-03-28T17:00:00Z' },
  topRivalry: { a: kari, b: ola, games: 14, aWins: 7, draws: 2, bWins: 5 },
  ...over,
})

function renderAt(path: string) {
  localStorage.setItem('tf-lang', 'en')
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <I18nProvider>
        <ToastProvider>
          <MemoryRouter initialEntries={[path]}>
            <Routes>
              <Route path="/year/:year" element={<YearPage />} />
              <Route path="/year/:year/:username" element={<YearPage />} />
            </Routes>
          </MemoryRouter>
        </ToastProvider>
      </I18nProvider>
    </QueryClientProvider>,
  )
}

const current = () => screen.getByRole('group', { name: /^Card \d+ of \d+/ })

beforeEach(() => {
  vi.mocked(yearApi.player).mockReset()
  vi.mocked(yearApi.group).mockReset()
  vi.mocked(shareCard).mockReset()
})

describe("a player's year", () => {
  it('starts with your intro and steps through the cards with buttons and keys', async () => {
    vi.mocked(yearApi.player).mockResolvedValue(kariYear())
    renderAt('/year/2026/kari_nordmann')
    expect(await screen.findByText(y.introYou)).toBeInTheDocument()
    expect(yearApi.player).toHaveBeenCalledWith(2026, 'kari_nordmann')
    expect(screen.getByRole('link', { name: y.yourYear })).toHaveAttribute('aria-current', 'page')

    await userEvent.click(screen.getByRole('button', { name: y.next }))
    expect(within(current()).getByText(y.gamesPlayed(24))).toBeInTheDocument()
    expect(within(current()).getByText(y.mostGamesYou)).toBeInTheDocument()
    expect(within(current()).getByLabelText('24')).toBeInTheDocument()

    fireEvent.keyDown(screen.getByRole('region', { name: y.story }), { key: 'ArrowRight' })
    expect(within(current()).getByText('+84')).toBeInTheDocument()
    expect(within(current()).getByText(y.ratingFromTo(1500, 1584))).toBeInTheDocument()

    fireEvent.keyDown(screen.getByRole('region', { name: y.story }), { key: 'ArrowLeft' })
    expect(within(current()).getByText(y.gamesPlayed(24))).toBeInTheDocument()

    fireEvent.keyDown(screen.getByRole('region', { name: y.story }), { key: 'End' })
    expect(current()).toHaveAccessibleName(/In short/)
    expect(screen.getByRole('button', { name: y.next })).toBeDisabled()
  })

  it('has a card for each highlight, and leaves out the ones without data', async () => {
    vi.mocked(yearApi.player).mockResolvedValue(kariYear())
    renderAt('/year/2026/kari_nordmann')
    await screen.findByText(y.introYou)
    const steps = screen.getAllByRole('button', { name: /^Card \d+ of/ })
    // intro, games, rating, month, upset, rival, streak, awards, weekday, summary (no opening, no kudos)
    expect(steps).toHaveLength(10)

    await userEvent.click(steps[4])
    expect(within(current()).getByText('18%')).toBeInTheDocument()
    expect(within(current()).getByText(y.upsetYou('Ola Hansen', 1650))).toBeInTheDocument()
    expect(within(current()).getByRole('link', { name: y.seeGame })).toHaveAttribute('href', '/games/9')

    await userEvent.click(steps[5])
    expect(within(current()).getByText(y.rivalGames(9))).toBeInTheDocument()
    expect(within(current()).getByText(y.rivalRecord(5, 3, 1))).toBeInTheDocument()

    await userEvent.click(steps[8])
    expect(within(current()).getByText('Tuesday')).toBeInTheDocument()
    expect(within(current()).getByText(y.weekdayBody(11, 24, 'Tuesdays'))).toBeInTheDocument()
  })

  it("talks about someone else's year in the third person", async () => {
    vi.mocked(yearApi.player).mockResolvedValue(kariYear({ player: ola }))
    renderAt('/year/2026/ola_hansen')
    expect(await screen.findByText(y.introPlayer('Ola'))).toBeInTheDocument()
    expect(screen.getByRole('link', { name: y.yourYear })).not.toHaveAttribute('aria-current')
  })

  it('says so when there are no games that year', async () => {
    vi.mocked(yearApi.player).mockResolvedValue(kariYear({ games: 0, ratingStart: null, ratingEnd: null, bestMonth: null, biggestUpset: null, rival: null, longestWinStreak: null, busiestWeekday: null, awards: [] }))
    renderAt('/year/2026/kari_nordmann')
    expect(await screen.findByText(y.emptyYou(2026))).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: /^Card \d+ of/ })).toHaveLength(1)
  })

  it('shares the current card as an image', async () => {
    vi.mocked(yearApi.player).mockResolvedValue(kariYear())
    vi.mocked(shareCard).mockResolvedValue('downloaded')
    renderAt('/year/2026/kari_nordmann')
    await screen.findByText(y.introYou)
    await userEvent.click(screen.getByRole('button', { name: y.next }))
    await userEvent.click(screen.getAllByRole('button', { name: y.share })[0]) // one for phones, one for wider screens; CSS shows one
    expect(shareCard).toHaveBeenCalledWith(expect.any(HTMLElement), 'trafonsjakk-2026-kari_nordmann-2.png', expect.stringContaining('24'))
    expect(await screen.findByText(y.downloaded)).toBeInTheDocument()
  })

  it('tells you when the image fails', async () => {
    vi.mocked(yearApi.player).mockResolvedValue(kariYear())
    vi.mocked(shareCard).mockRejectedValue(new Error('canvas'))
    renderAt('/year/2026/kari_nordmann')
    await screen.findByText(y.introYou)
    await userEvent.click(screen.getAllByRole('button', { name: y.share })[0]) // one for phones, one for wider screens; CSS shows one
    expect(await screen.findByText(y.shareFailed)).toBeInTheDocument()
  })

  it('rejects years that are out of range without asking the server', async () => {
    renderAt('/year/1999/kari_nordmann')
    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(yearApi.player).not.toHaveBeenCalled()
  })
})

describe("the group's year", () => {
  it('walks through the highlights of the year', async () => {
    vi.mocked(yearApi.group).mockResolvedValue(groupYear())
    renderAt('/year/2026')
    expect(await screen.findByText(y.groupIntroBody(80, 6))).toBeInTheDocument()
    expect(within(current()).getByText(y.introBodySoFar)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: y.groupYear })).toHaveAttribute('aria-current', 'page')
    const steps = screen.getAllByRole('button', { name: /^Card \d+ of/ })
    // intro, game, giant killer, awards, games, climbers, streak, rivalry, facts
    expect(steps).toHaveLength(9)

    await userEvent.click(steps[1])
    expect(within(current()).getByText('0–1')).toBeInTheDocument()
    expect(within(current()).getByText(y.buzzBody(6, 9))).toBeInTheDocument()

    await userEvent.click(steps[2])
    expect(within(current()).getByText('9%')).toBeInTheDocument()
    expect(within(current()).getByText(y.giantKillerBody('Per Olsen', 'Kari Nordmann', 1290, 1700))).toBeInTheDocument()

    await userEvent.click(steps[3])
    expect(within(current()).getByText(y.awardLeaderBody(3))).toBeInTheDocument()

    await userEvent.click(steps[8])
    expect(within(current()).getByText(y.drawShare('13%'))).toBeInTheDocument()
    expect(within(current()).getByText(y.movesRecorded(12))).toBeInTheDocument()
  })
})

describe('YearBanner', () => {
  const wrap = (now: Date) => {
    localStorage.setItem('tf-lang', 'en')
    return render(
      <I18nProvider>
        <MemoryRouter>
          <YearBanner now={now} />
        </MemoryRouter>
      </I18nProvider>,
    )
  }

  it('invites you to your year in December', () => {
    wrap(new Date(2026, 11, 3))
    expect(screen.getByRole('heading', { name: y.bannerTitle(2026) })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: y.bannerOpen })).toHaveAttribute('href', '/year/2026/kari_nordmann')
  })

  it('stays away the rest of the year', () => {
    const { container } = wrap(new Date(2026, 9, 3))
    expect(container).toBeEmptyDOMElement()
  })
})
