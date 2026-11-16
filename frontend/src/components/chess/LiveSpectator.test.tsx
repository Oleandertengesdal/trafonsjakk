import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { LiveGame } from '../../api/types'
import type { EngineLine } from '../../chess/analysis'
import { Engine } from '../../chess/engine'
import { en } from '../../i18n/en'
import { I18nProvider } from '../../i18n/I18nProvider'
import { kari, ola } from '../../test/fixtures'
import LiveSpectator from './LiveSpectator'

vi.mock('../../chess/engine', () => ({ Engine: { load: vi.fn() }, ENGINE_NAME: 'Stockfish 18', LIVE_DEPTH: 20 }))

const l = en.live
const v = en.viewer

const game = (moves: string | null): LiveGame => ({
  id: 7,
  white: kari,
  black: ola,
  status: 'LIVE',
  moves,
  version: 1,
  startedAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
  gameId: null,
  canRecord: false,
})

/** A fake Stockfish that answers every position with the same two lines. */
function fakeEngine(lines: EngineLine[]) {
  return {
    analyse: vi.fn(async (_fen: string, onLine: (line: EngineLine) => void) => lines.forEach(onLine)),
    terminate: vi.fn(),
  }
}

function setup(moves: string | null) {
  localStorage.setItem('tf-lang', 'en')
  const ui = (m: string | null) => (
    <I18nProvider>
      <LiveSpectator game={game(m)} />
    </I18nProvider>
  )
  const r = render(ui(moves))
  return { update: (m: string) => r.rerender(ui(m)) }
}

describe('watching a live game', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.mocked(Engine.load).mockReset()
    vi.spyOn(Element.prototype, 'getBoundingClientRect').mockReturnValue({
      x: 0, y: 0, top: 0, left: 0, bottom: 40, right: 40, width: 40, height: 40, toJSON: () => ({}),
    } as DOMRect)
  })

  it('follows the game, and lets you step back and return to live', async () => {
    const { update } = setup('1. e4 e5')
    const status = () => screen.getByTestId('spectator-status')
    expect(status()).toHaveTextContent(v.afterMove('1... e5'))
    expect(screen.getByText(l.followingLive)).toBeInTheDocument()

    // A new move: the board follows
    update('1. e4 e5 2. Nf3')
    expect(status()).toHaveTextContent(v.afterMove('2. Nf3'))

    // Look back at the first move; new moves don't pull you away
    await userEvent.click(within(screen.getByRole('list', { name: en.moves.list })).getByRole('button', { name: 'e4' }))
    expect(status()).toHaveTextContent(v.afterMove('1. e4'))
    update('1. e4 e5 2. Nf3 Nc6')
    expect(status()).toHaveTextContent(v.afterMove('1. e4'))
    await userEvent.click(screen.getByRole('button', { name: l.returnToLive(3) }))
    expect(status()).toHaveTextContent(v.afterMove('2... Nc6'))
    expect(screen.getByText(l.followingLive)).toBeInTheDocument()
  })

  it('analyses the position on the board with Stockfish when switched on', async () => {
    const engine = fakeEngine([
      { rank: 1, depth: 16, score: { cp: 34 }, pv: ['g1f3', 'b8c6', 'f1b5'] },
      { rank: 2, depth: 16, score: { cp: -12 }, pv: ['f2f4'] },
    ])
    vi.mocked(Engine.load).mockResolvedValue(engine as unknown as Engine)
    setup('1. e4 e5')
    expect(screen.getByText(l.analysisFairPlay)).toBeInTheDocument()
    expect(Engine.load).not.toHaveBeenCalled()

    await userEvent.click(screen.getByRole('switch', { name: en.engine.switch }))
    const lines = await screen.findByRole('list', { name: en.engine.lines })
    expect(within(lines).getAllByRole('listitem')).toHaveLength(2)
    expect(within(lines).getByText('2. Nf3')).toBeInTheDocument()
    expect(within(lines).getByText('Nc6 3. Bb5')).toBeInTheDocument()
    expect(within(lines).getByText('+0.3')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: en.analysis.evalBar('+0.3') })).toBeInTheDocument()
    expect(screen.getByText(en.engine.depth(16, 20))).toBeInTheDocument()

    // Stepping back analyses that position instead
    await userEvent.click(within(screen.getByRole('list', { name: en.moves.list })).getByRole('button', { name: 'e4' }))
    await waitFor(() => expect(engine.analyse).toHaveBeenLastCalledWith(expect.stringContaining(' b KQkq'), expect.any(Function)))

    // Switching off stops the engine, and the choice is remembered
    await userEvent.click(screen.getByRole('switch', { name: en.engine.switch }))
    expect(engine.terminate).toHaveBeenCalled()
    expect(localStorage.getItem('tf-live-analysis')).toBe('off')
  })

  it('says so when the engine can’t run here', async () => {
    vi.mocked(Engine.load).mockRejectedValue(new Error('no wasm'))
    setup('1. d4')
    await userEvent.click(screen.getByRole('switch', { name: en.engine.switch }))
    expect(await screen.findByText(en.analysis.failed)).toBeInTheDocument()
  })
})
