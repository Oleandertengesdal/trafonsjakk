import { QueryClientProvider } from '@tanstack/react-query'
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import { onlineApi } from '../api/endpoints'
import { queryClient } from '../api/queryClient'
import type { OnlineGame } from '../api/types'
import { ToastProvider } from '../components/ui/Toast'
import { en } from '../i18n/en'
import { I18nProvider } from '../i18n/I18nProvider'
import { kari, ola } from '../test/fixtures'
import { OnlineGamePage } from './OnlineGamePage'

vi.mock('../api/endpoints', () => ({
  onlineApi: {
    get: vi.fn(),
    move: vi.fn(),
    resign: vi.fn(),
    abort: vi.fn(),
    offerDraw: vi.fn(),
    acceptDraw: vi.fn(),
    declineDraw: vi.fn(),
    rematch: vi.fn(),
  },
}))
vi.mock('../api/queryClient', async (orig) => ({ ...(await orig<object>()), invalidateResults: vi.fn() }))

const p = en.play
const START = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1'
const AFTER_E4 = 'rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1'

const game = (over: Partial<OnlineGame> = {}): OnlineGame => ({
  id: 9,
  white: kari,
  black: ola,
  rated: true,
  timeControl: { kind: 'LIVE', initialSeconds: 600, incrementSeconds: 5, daysPerMove: 0, label: '10+5' },
  status: 'ACTIVE',
  result: null,
  endReason: null,
  moves: null,
  plies: 0,
  lastMove: null,
  fen: START,
  turn: 'WHITE',
  clock: {
    whiteMs: 600_000,
    blackMs: 600_000,
    running: true,
    toMove: 'WHITE',
    serverTime: new Date().toISOString(),
    firstMoveBy: new Date(Date.now() + 60_000).toISOString(),
    deadline: null,
  },
  drawOfferBy: null,
  myColour: 'WHITE',
  canAbort: true,
  gameId: null,
  ratingChange: null,
  startedAt: new Date().toISOString(),
  endedAt: null,
  version: 0,
  ...over,
})

function renderPage() {
  localStorage.setItem('tf-lang', 'en')
  queryClient.clear()
  const utils = render(
    <QueryClientProvider client={queryClient}>
      <I18nProvider>
        <ToastProvider>
          <MemoryRouter initialEntries={['/play/9']}>
            <Routes>
              <Route path="/play/:id" element={<OnlineGamePage />} />
              <Route path="/play" element={<p>lobby</p>} />
            </Routes>
          </MemoryRouter>
        </ToastProvider>
      </I18nProvider>
    </QueryClientProvider>,
  )
  const square = (sq: string) => {
    const el = utils.container.querySelector(`[data-square="${sq}"]`)
    if (!el) throw new Error(`no square ${sq}`)
    return el
  }
  const tap = (...squares: string[]) => {
    for (const sq of squares) act(() => fireEvent.click(square(sq)))
  }
  return { ...utils, square, tap }
}

describe('OnlineGamePage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    // jsdom has no layout; the board measures squares to animate moves
    vi.spyOn(Element.prototype, 'getBoundingClientRect').mockReturnValue({
      x: 0, y: 0, top: 0, left: 0, bottom: 40, right: 40, width: 40, height: 40, toJSON: () => ({}),
    } as DOMRect)
  })

  it('sends a move with the ply it was made on and how long it took, and shows it straight away', async () => {
    vi.mocked(onlineApi.get).mockResolvedValue(game())
    let resolve: (g: OnlineGame) => void = () => {}
    vi.mocked(onlineApi.move).mockImplementation(() => new Promise((r) => (resolve = r)))
    const r = renderPage()
    expect(await screen.findByText(p.yourMove)).toBeInTheDocument()
    await waitFor(() => r.square('e2'))
    r.tap('e2', 'e4')
    expect(onlineApi.move).toHaveBeenCalledWith(9, 'e2e4', 0, expect.any(Number))
    // Shown before the server answers
    await waitFor(() => expect(r.square('e4').querySelector('[data-piece="wP"]')).not.toBeNull())
    expect(r.square('e2').querySelector('[data-piece]')).toBeNull()
    act(() =>
      resolve(game({ fen: AFTER_E4, plies: 1, turn: 'BLACK', lastMove: 'e2e4', moves: '1. e4', version: 1, clock: { ...game().clock, toMove: 'BLACK' } })),
    )
    expect(await screen.findByText(p.waitingForMove(ola.realName))).toBeInTheDocument()
    expect(screen.getByText('e4')).toBeInTheDocument()
    // The last move is marked on the board
    expect(r.square('e2').querySelector('[data-last-move]')).not.toBeNull()
    expect(r.square('e4').querySelector('[data-last-move]')).not.toBeNull()
    expect(r.square('d2').querySelector('[data-last-move]')).toBeNull()
  })

  it('does not let you move when it is not your turn, nor a spectator at all', async () => {
    vi.mocked(onlineApi.get).mockResolvedValue(game({ myColour: null, canAbort: false }))
    const r = renderPage()
    expect(await screen.findByText(p.spectating)).toBeInTheDocument()
    await waitFor(() => r.square('e2'))
    r.tap('e2', 'e4')
    expect(onlineApi.move).not.toHaveBeenCalled()
    expect(screen.queryByRole('button', { name: p.resign })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: p.abort })).not.toBeInTheDocument()
  })

  it('shows what the server has when a move is refused', async () => {
    vi.mocked(onlineApi.get).mockResolvedValue(game())
    vi.mocked(onlineApi.move).mockRejectedValue(new ApiError(409, 'stale', { code: 'online.out_of_date' }))
    const r = renderPage()
    await screen.findByText(p.yourMove)
    await waitFor(() => r.square('e2'))
    r.tap('e2', 'e4')
    expect(await screen.findByText(en.errors.codes['online.out_of_date'] as string)).toBeInTheDocument()
    await waitFor(() => expect(onlineApi.get).toHaveBeenCalledTimes(2))
  })

  it('counts the clocks down and offers cancelling only before both have moved', async () => {
    vi.mocked(onlineApi.get).mockResolvedValue(
      game({ plies: 2, canAbort: false, clock: { ...game().clock, firstMoveBy: null, whiteMs: 15_000 } }),
    )
    renderPage()
    expect(await screen.findByRole('timer', { name: p.clockOf(kari.realName) })).toHaveTextContent(/^0:1[45]\.\d$/)
    expect(screen.getByRole('timer', { name: p.clockOf(ola.realName) })).toHaveTextContent('10:00')
    expect(screen.queryByRole('button', { name: p.abort })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: p.offerDraw })).toBeInTheDocument()
  })

  it('resigns after asking', async () => {
    vi.mocked(onlineApi.get).mockResolvedValue(game({ plies: 2, canAbort: false, clock: { ...game().clock, firstMoveBy: null } }))
    vi.mocked(onlineApi.resign).mockResolvedValue(
      game({ status: 'FINISHED', result: 'BLACK_WIN', endReason: 'RESIGNATION', gameId: 77, clock: { ...game().clock, running: false } }),
    )
    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: p.resign }))
    const dialog = await screen.findByRole('dialog')
    await userEvent.click(within(dialog).getByRole('button', { name: p.resign }))
    expect(onlineApi.resign).toHaveBeenCalledWith(9)
    expect(await screen.findByText(p.outcome.BLACK_WIN(ola.realName))).toBeInTheDocument()
    expect(screen.getByText(p.endReasons.RESIGNATION)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: p.openGame })).toHaveAttribute('href', '/games/77')
  })

  it('answers a draw offer', async () => {
    vi.mocked(onlineApi.get).mockResolvedValue(game({ plies: 4, canAbort: false, drawOfferBy: ola.id, clock: { ...game().clock, firstMoveBy: null } }))
    vi.mocked(onlineApi.acceptDraw).mockResolvedValue(
      game({ status: 'FINISHED', result: 'DRAW', endReason: 'AGREEMENT', clock: { ...game().clock, running: false } }),
    )
    renderPage()
    expect(await screen.findByText(p.drawOfferedToYou(ola.realName))).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: p.acceptDraw }))
    expect(await screen.findByText(p.endReasons.AGREEMENT)).toBeInTheDocument()
  })

  it('shows the result with the rating change, and offers a rematch', async () => {
    vi.mocked(onlineApi.get).mockResolvedValue(
      game({
        status: 'FINISHED',
        result: 'WHITE_WIN',
        endReason: 'TIMEOUT',
        gameId: 5,
        ratingChange: { white: { before: 1500, after: 1612 }, black: { before: 1500, after: 1388 } },
        clock: { ...game().clock, running: false },
      }),
    )
    vi.mocked(onlineApi.rematch).mockResolvedValue({} as never)
    renderPage()
    expect(await screen.findByText(p.outcome.WHITE_WIN(kari.realName))).toBeInTheDocument()
    expect(screen.getByText(p.endReasons.TIMEOUT)).toBeInTheDocument()
    expect(screen.getByText('1612')).toBeInTheDocument()
    expect(screen.getByText(p.countsOnline)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: p.rematch }))
    expect(onlineApi.rematch).toHaveBeenCalledWith(9)
    expect(await screen.findByText('lobby')).toBeInTheDocument()
  })

  it('says when a game was cancelled', async () => {
    vi.mocked(onlineApi.get).mockResolvedValue(game({ status: 'ABORTED', canAbort: false, clock: { ...game().clock, running: false } }))
    renderPage()
    expect(await screen.findByText(p.aborted)).toBeInTheDocument()
  })

  it('shows a 404 for a game that does not exist', async () => {
    vi.mocked(onlineApi.get).mockRejectedValue(new ApiError(404, 'nope', { code: 'online.not_found' }))
    renderPage()
    expect(await screen.findByText(en.errorPage.notFound.online.title)).toBeInTheDocument()
  })
})

