import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { EngineLine } from '../../chess/analysis'
import { Engine } from '../../chess/engine'
import { en } from '../../i18n/en'
import { I18nProvider } from '../../i18n/I18nProvider'
import GameViewer from './GameViewer'

vi.mock('../../chess/engine', () => ({ Engine: { load: vi.fn() }, DEPTH: 14, ENGINE_NAME: 'Stockfish 18', LIVE_DEPTH: 20 }))

const e = en.engine
const v = en.viewer

/** A fake Stockfish: White's best is Nf3, then Bc4, whatever the position. */
const LINES: EngineLine[] = [
  { rank: 1, depth: 18, score: { cp: 41 }, pv: ['g1f3', 'b8c6'] },
  { rank: 2, depth: 18, score: { cp: 22 }, pv: ['f1c4'] },
]

function setup() {
  localStorage.setItem('tf-lang', 'en')
  const engine = { analyse: vi.fn(async (_fen: string, onLine: (l: EngineLine) => void) => LINES.forEach(onLine)), terminate: vi.fn() }
  vi.mocked(Engine.load).mockResolvedValue(engine as unknown as Engine)
  render(
    <I18nProvider>
      <GameViewer moves="1. e4 e5 2. Nf3 Nc6" orientation="white" whiteName="Kari" blackName="Ola" />
    </I18nProvider>,
  )
  return engine
}

const moveList = () => within(screen.getByRole('list', { name: en.moves.list }))

describe('going through a finished game with Stockfish', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.mocked(Engine.load).mockReset()
    vi.spyOn(Element.prototype, 'getBoundingClientRect').mockReturnValue({
      x: 0, y: 0, top: 0, left: 0, bottom: 40, right: 40, width: 40, height: 40, toJSON: () => ({}),
    } as DOMRect)
  })

  it('is off until you switch it on', () => {
    setup()
    expect(screen.getByText(e.viewerIntro)).toBeInTheDocument()
    expect(Engine.load).not.toHaveBeenCalled()
  })

  it('shows the best lines for the position, and lets you try your own moves', async () => {
    const engine = setup()
    await userEvent.click(moveList().getByRole('button', { name: 'e5' }))
    await userEvent.click(screen.getByRole('switch', { name: e.switch }))
    const lines = await screen.findByRole('list', { name: e.lines })
    expect(within(lines).getByText('2. Nf3')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: en.analysis.evalBar('+0.4') })).toBeInTheDocument()

    // The engine's choice is what was played: that's just the next move of the game
    await userEvent.click(within(lines).getByRole('button', { name: new RegExp(e.play('2. Nf3').replace('.', '\\.')) }))
    expect(screen.getByTestId('viewer-status')).toHaveTextContent(v.afterMove('2. Nf3'))

    // Back one, then try something else: your own line
    await userEvent.click(screen.getByRole('button', { name: v.prev }))
    await userEvent.click(within(await screen.findByRole('list', { name: e.lines })).getByRole('button', { name: /Bc4/ }))
    const line = screen.getByTestId('viewer-line')
    expect(line).toHaveTextContent(e.variation('1... e5'))
    expect(line).toHaveTextContent('2. Bc4')
    expect(engine.analyse).toHaveBeenLastCalledWith(expect.stringContaining('2B1P3'), expect.any(Function))

    // One step back leaves your line, at the move it branched from
    await userEvent.click(screen.getByRole('button', { name: v.prev }))
    expect(screen.queryByTestId('viewer-line')).not.toBeInTheDocument()
    expect(screen.getByTestId('viewer-status')).toHaveTextContent(v.afterMove('1... e5'))
  })

  it('takes you back to the game from a longer line', async () => {
    setup()
    await userEvent.click(moveList().getByRole('button', { name: 'e5' }))
    await userEvent.click(screen.getByRole('switch', { name: e.switch }))
    await userEvent.click(within(await screen.findByRole('list', { name: e.lines })).getByRole('button', { name: /Bc4/ }))
    expect(screen.getByTestId('viewer-line')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: e.backToGame }))
    expect(screen.getByTestId('viewer-status')).toHaveTextContent(v.afterMove('1... e5'))
  })

  it('lets you tap a piece and then its square, for phones', async () => {
    setup()
    await userEvent.click(moveList().getByRole('button', { name: 'e5' }))
    await userEvent.click(screen.getByRole('switch', { name: e.switch }))
    await screen.findByRole('list', { name: e.lines })
    const square = (id: string) => document.querySelector(`[data-square="${id}"]`) as HTMLElement
    await userEvent.click(square('b1'))
    await userEvent.click(square('c3'))
    expect(screen.getByTestId('viewer-line')).toHaveTextContent('2. Nc3')
  })
})
