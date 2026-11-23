import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { analysisApi } from '../../api/endpoints'
import type { SavedAnalysis } from '../../api/types'
import { positions, type EngineLine, type PositionEval } from '../../chess/analysis'
import { Engine } from '../../chess/engine'
import { en } from '../../i18n/en'
import { I18nProvider } from '../../i18n/I18nProvider'
import GameViewer from './GameViewer'

vi.mock('../../chess/engine', () => ({ Engine: { load: vi.fn() }, DEPTH: 14, ENGINE_NAME: 'Stockfish 18', LIVE_DEPTH: 18 }))
vi.mock('../../api/endpoints', () => ({
  analysisApi: { get: vi.fn(), save: vi.fn(), calibration: vi.fn() },
}))

const a = en.analysis
const e = en.engine
const v = en.viewer
const FOOLS_MATE = '1. f3 e5 2. g4 Qh4#'
const SANS = ['f3', 'e5', 'g4', 'Qh4#']

/** What our fake Stockfish says about each position of the fool's mate (the last one is mate: no engine). */
const SCRIPT: PositionEval[] = [
  { score: { cp: 20 }, best: 'e2e4' },
  { score: { cp: -50 }, best: 'e7e5' },
  { score: { cp: -40 }, best: 'b1c3' },
  { score: { mate: 1, by: 'b' }, best: 'd8h4' },
]
/** …and its live lines, whatever the position (only legal ones show). */
const LINES: EngineLine[] = [
  { rank: 1, depth: 16, score: { cp: 41 }, pv: ['b1c3', 'b8c6'] },
  { rank: 2, depth: 16, score: { cp: 22 }, pv: ['g1f3'] },
]

function fakeEngine() {
  const fens = positions(SANS).map((p) => p.fen)
  return {
    evaluate: vi.fn(async (fen: string): Promise<PositionEval> => SCRIPT[fens.indexOf(fen)] ?? { score: { cp: 0 }, best: null }),
    analyse: vi.fn(async (_fen: string, onLine: (l: EngineLine) => void) => LINES.forEach(onLine)),
    halt: vi.fn(async () => {}),
    terminate: vi.fn(),
  }
}

function setup(moves = FOOLS_MATE, gameId?: number) {
  localStorage.setItem('tf-lang', 'en')
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <I18nProvider>
        <GameViewer gameId={gameId} moves={moves} orientation="white" whiteName="Kari" blackName="Ola" />
      </I18nProvider>
    </QueryClientProvider>,
  )
}

const saved = (evals: PositionEval[]): SavedAnalysis => ({
  gameId: 7,
  depth: 14,
  evals,
  white: { moves: 2, accuracy: 40 },
  black: { moves: 2, accuracy: 99 },
  analysedAt: '2026-11-20T10:00:00Z',
})

const analyse = () => screen.getByRole('button', { name: e.toggle })
const moveList = () => within(screen.getByRole('list', { name: en.moves.list }))

describe('analysing a game', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.mocked(Engine.load).mockReset()
    vi.mocked(analysisApi.get).mockReset().mockResolvedValue(null)
    vi.mocked(analysisApi.save).mockReset().mockImplementation(async (_id, _depth, evals) => saved(evals))
    vi.mocked(analysisApi.calibration).mockReset().mockResolvedValue({ samples: 3, minSamples: 30, intercept: null, slope: null, low: null, high: null })
    vi.spyOn(Element.prototype, 'getBoundingClientRect').mockReturnValue({
      x: 0, y: 0, top: 0, left: 0, bottom: 40, right: 40, width: 40, height: 40, toJSON: () => ({}),
    } as DOMRect)
  })

  it('is off until you ask for it, and the page stays simple', () => {
    setup()
    expect(analyse()).toHaveAttribute('aria-pressed', 'false')
    expect(Engine.load).not.toHaveBeenCalled()
    expect(screen.getByRole('table', { name: v.statsTitle })).toBeInTheDocument()
    expect(screen.queryByRole('img', { name: a.graph })).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: e.title })).not.toBeInTheDocument()
  })

  it('uses one engine: first the review, then the position on the board', async () => {
    const engine = fakeEngine()
    vi.mocked(Engine.load).mockResolvedValue(engine as unknown as Engine)
    setup()
    await userEvent.click(analyse())
    expect(analyse()).toHaveAttribute('aria-pressed', 'true')

    // The review: marks, graph and accuracy
    const list = await screen.findByRole('list', { name: en.moves.list })
    await waitFor(() => expect(within(list).getByRole('button', { name: 'g4??' })).toBeInTheDocument())
    expect(engine.evaluate).toHaveBeenCalledTimes(4) // the mated position needs no engine
    expect(screen.getByRole('img', { name: a.graph })).toBeInTheDocument()
    const accuracy = screen.getByRole('region', { name: a.title })
    expect(within(accuracy).getByRole('row', { name: /Kari/ })).toHaveTextContent('1??')
    expect(within(accuracy).getByRole('row', { name: /Ola/ })).toHaveTextContent(a.clean)
    expect(Engine.load).toHaveBeenCalledTimes(1)

    // Then the live lines, for the position on the board
    await userEvent.click(within(list).getByRole('button', { name: 'e5' }))
    expect(screen.getByTestId('move-comment')).toHaveTextContent(a.best)
    const lines = await screen.findByRole('list', { name: e.lines })
    expect(await within(lines).findByText('2. Nc3')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: a.evalBar('+0.4') })).toBeInTheDocument()
    expect(engine.analyse).toHaveBeenLastCalledWith(positions(SANS)[2].fen, expect.any(Function))

    await userEvent.click(within(list).getByRole('button', { name: 'g4??' }))
    expect(screen.getByTestId('viewer-status')).toHaveTextContent(`${v.afterMove('2. g4')}??`)
    expect(screen.getByTestId('move-comment')).toHaveTextContent(`${a.judgement.blunder}: ${a.allowedMate(1)}. ${a.better('2. Nc3')}`)

    // Off again: the engine stops
    await userEvent.click(analyse())
    expect(engine.terminate).toHaveBeenCalled()
    expect(screen.queryByRole('img', { name: a.graph })).not.toBeInTheDocument()
  })

  it('remembers the review on this device', async () => {
    vi.mocked(Engine.load).mockResolvedValue(fakeEngine() as unknown as Engine)
    const first = setup()
    await userEvent.click(analyse())
    await waitFor(() => expect(screen.getByRole('img', { name: a.graph })).toBeInTheDocument())
    first.unmount()

    const engine = fakeEngine()
    vi.mocked(Engine.load).mockResolvedValue(engine as unknown as Engine)
    setup() // analysis is still on: remembered too
    expect(screen.getByRole('img', { name: a.graph })).toBeInTheDocument()
    // The game ends in mate, so step back to give the engine something to think about
    await userEvent.click(moveList().getByRole('button', { name: 'e5' }))
    await waitFor(() => expect(engine.analyse).toHaveBeenCalled())
    expect(engine.evaluate).not.toHaveBeenCalled()
  })

  it('lets you try your own moves', async () => {
    const engine = fakeEngine()
    vi.mocked(Engine.load).mockResolvedValue(engine as unknown as Engine)
    setup('1. e4 e5 2. Nf3 Nc6')
    await userEvent.click(analyse())
    await userEvent.click(moveList().getByRole('button', { name: 'e5' }))

    // The engine's choice is what was played: that's just the next move of the game
    const lines = await screen.findByRole('list', { name: e.lines })
    await userEvent.click(await within(lines).findByRole('button', { name: /2\. Nf3/ }))
    expect(screen.getByTestId('viewer-status')).toHaveTextContent(v.afterMove('2. Nf3'))

    // Something else starts your own line, which the engine follows
    await userEvent.click(screen.getByRole('button', { name: v.prev }))
    await userEvent.click(await within(await screen.findByRole('list', { name: e.lines })).findByRole('button', { name: /2\. Nc3/ }))
    expect(screen.getByTestId('viewer-line')).toHaveTextContent(e.variation('1... e5'))
    expect(screen.getByTestId('viewer-line')).toHaveTextContent('2. Nc3')
    await waitFor(() => expect(engine.analyse).toHaveBeenLastCalledWith(expect.stringContaining('2N5'), expect.any(Function)))

    // Tap a piece, then a square (for phones)
    const square = (id: string) => document.querySelector(`[data-square="${id}"]`) as HTMLElement
    await userEvent.click(square('g8'))
    await userEvent.click(square('f6'))
    expect(screen.getByTestId('viewer-line')).toHaveTextContent('2. Nc3 Nf6')

    // Back one move, then back to the game
    await userEvent.click(screen.getByRole('button', { name: v.prev }))
    expect(screen.getByTestId('viewer-line')).not.toHaveTextContent('Nf6')
    await userEvent.click(screen.getByRole('button', { name: e.backToGame }))
    expect(screen.queryByTestId('viewer-line')).not.toBeInTheDocument()
    expect(screen.getByTestId('viewer-status')).toHaveTextContent(v.afterMove('1... e5'))
  })

  it('says so when the engine can’t run here, and can try again', async () => {
    vi.mocked(Engine.load).mockRejectedValueOnce(new Error('no wasm'))
    setup()
    await userEvent.click(analyse())
    expect(await screen.findByText(a.failed)).toBeInTheDocument()
    vi.mocked(Engine.load).mockResolvedValue(fakeEngine() as unknown as Engine)
    await userEvent.click(analyse())
    await userEvent.click(analyse())
    await waitFor(() => expect(screen.getByRole('img', { name: a.graph })).toBeInTheDocument())
  })

  it('uses the review someone else saved, without running it again', async () => {
    vi.mocked(analysisApi.get).mockResolvedValue(saved(SCRIPT.concat([{ score: { mate: 0, by: 'b' }, best: null }])))
    const engine = fakeEngine()
    vi.mocked(Engine.load).mockResolvedValue(engine as unknown as Engine)
    setup(FOOLS_MATE, 7)
    await userEvent.click(analyse())
    const list = await screen.findByRole('list', { name: en.moves.list })
    await waitFor(() => expect(within(list).getByRole('button', { name: 'g4??' })).toBeInTheDocument())
    expect(analysisApi.get).toHaveBeenCalledWith(7)
    expect(engine.evaluate).not.toHaveBeenCalled()
    expect(analysisApi.save).not.toHaveBeenCalled()
  })

  it('saves a review it made itself, for everyone else', async () => {
    vi.mocked(Engine.load).mockResolvedValue(fakeEngine() as unknown as Engine)
    setup(FOOLS_MATE, 7)
    await userEvent.click(analyse())
    await waitFor(() => expect(analysisApi.save).toHaveBeenCalledTimes(1))
    const [id, depth, evals] = vi.mocked(analysisApi.save).mock.calls[0]
    expect(id).toBe(7)
    expect(depth).toBe(14)
    expect(evals).toHaveLength(SANS.length + 1)
    expect(evals[0]).toEqual(SCRIPT[0])
  })

  it('still reviews in the browser when the server can’t be asked', async () => {
    vi.mocked(analysisApi.get).mockRejectedValue(new Error('offline'))
    const engine = fakeEngine()
    vi.mocked(Engine.load).mockResolvedValue(engine as unknown as Engine)
    setup(FOOLS_MATE, 7)
    await userEvent.click(analyse())
    // It asks once more first (a second later), then gives up on the server
    await waitFor(() => expect(screen.getByRole('img', { name: a.graph })).toBeInTheDocument(), { timeout: 5000 })
    expect(engine.evaluate).toHaveBeenCalled()
    expect(analysisApi.save).not.toHaveBeenCalled()
  })
})
