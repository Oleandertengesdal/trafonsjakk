import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { positions, type PositionEval } from '../../chess/analysis'
import { Engine } from '../../chess/engine'
import { en } from '../../i18n/en'
import { I18nProvider } from '../../i18n/I18nProvider'
import GameViewer from './GameViewer'

vi.mock('../../chess/engine', () => ({ Engine: { load: vi.fn() }, DEPTH: 14 }))

const a = en.analysis
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

function fakeEngine() {
  const fens = positions(SANS).map((p) => p.fen)
  const engine = {
    evaluate: vi.fn(async (fen: string) => SCRIPT[fens.indexOf(fen)]),
    terminate: vi.fn(),
    stop: vi.fn(),
  }
  return engine
}

function setup() {
  localStorage.setItem('tf-lang', 'en')
  return render(
    <I18nProvider>
      <GameViewer moves={FOOLS_MATE} orientation="white" whiteName="Kari" blackName="Ola" />
    </I18nProvider>,
  )
}

describe('engine analysis', () => {
  beforeEach(() => {
    vi.mocked(Engine.load).mockReset()
    vi.spyOn(Element.prototype, 'getBoundingClientRect').mockReturnValue({
      x: 0, y: 0, top: 0, left: 0, bottom: 40, right: 40, width: 40, height: 40, toJSON: () => ({}),
    } as DOMRect)
  })

  it('is only run when you ask for it', () => {
    setup()
    expect(screen.getByRole('button', { name: a.run })).toBeInTheDocument()
    expect(screen.getByText(a.intro)).toBeInTheDocument()
    expect(Engine.load).not.toHaveBeenCalled()
    expect(screen.queryByRole('img', { name: a.graph })).not.toBeInTheDocument()
  })

  it('marks the blunder, says what was better and grades both players', async () => {
    const engine = fakeEngine()
    vi.mocked(Engine.load).mockResolvedValue(engine as unknown as Engine)
    setup()
    await userEvent.click(screen.getByRole('button', { name: a.run }))

    const table = await screen.findByRole('table', { name: a.title })
    expect(engine.evaluate).toHaveBeenCalledTimes(4) // the mated position needs no engine
    expect(engine.terminate).toHaveBeenCalled()
    const blunders = within(table).getByRole('row', { name: new RegExp(a.blunders.replace(/[?()]/g, '\\$&')) })
    expect(within(blunders).getAllByRole('cell').map((c) => c.textContent)).toEqual(['1', '0'])

    const list = screen.getByRole('list', { name: en.moves.list })
    expect(within(list).getByRole('button', { name: 'g4??' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: a.graph })).toBeInTheDocument()

    await userEvent.click(within(list).getByRole('button', { name: 'g4??' }))
    expect(screen.getByTestId('viewer-status')).toHaveTextContent(`${v.afterMove('2. g4')}??`)
    const comment = screen.getByTestId('move-comment')
    expect(comment).toHaveTextContent(`${a.judgement.blunder}: ${a.allowedMate(1)}.`)
    expect(comment).toHaveTextContent(a.better('2. Nc3'))
    expect(comment).toHaveTextContent(a.evaluation('−#1'))
    expect(comment).toHaveTextContent(a.bestHere('2... Qh4#'))
    expect(screen.getByRole('img', { name: a.evalBar('−#1') })).toBeInTheDocument()

    await userEvent.click(within(list).getByRole('button', { name: 'e5' }))
    expect(screen.getByTestId('move-comment')).toHaveTextContent(a.best)
  })

  it('remembers the analysis on this device', async () => {
    vi.mocked(Engine.load).mockResolvedValue(fakeEngine() as unknown as Engine)
    const first = setup()
    await userEvent.click(screen.getByRole('button', { name: a.run }))
    await screen.findByRole('table', { name: a.title })
    first.unmount()

    vi.mocked(Engine.load).mockClear()
    setup()
    expect(screen.getByRole('table', { name: a.title })).toBeInTheDocument()
    expect(Engine.load).not.toHaveBeenCalled()
  })

  it('says so when the engine fails, and can try again', async () => {
    vi.mocked(Engine.load).mockRejectedValueOnce(new Error('no wasm'))
    setup()
    await userEvent.click(screen.getByRole('button', { name: a.run }))
    expect(await screen.findByText(a.failed)).toBeInTheDocument()
    vi.mocked(Engine.load).mockResolvedValue(fakeEngine() as unknown as Engine)
    await userEvent.click(screen.getByRole('button', { name: a.retry }))
    expect(await screen.findByRole('table', { name: a.title })).toBeInTheDocument()
  })

  it('can be stopped, and stops the engine', async () => {
    const engine = fakeEngine()
    let release: (e: Engine) => void = () => {}
    vi.mocked(Engine.load).mockReturnValue(new Promise((r) => (release = r)))
    setup()
    await userEvent.click(screen.getByRole('button', { name: a.run }))
    expect(screen.getByText(a.loading)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: a.cancel }))
    release(engine as unknown as Engine)
    await waitFor(() => expect(engine.terminate).toHaveBeenCalled())
    expect(screen.getByRole('button', { name: a.run })).toBeInTheDocument()
    expect(engine.evaluate).not.toHaveBeenCalled()
  })
})
