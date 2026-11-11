import { act, fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { GameResult } from '../../api/types'
import { en } from '../../i18n/en'
import { I18nProvider } from '../../i18n/I18nProvider'
import MoveRecorder from './MoveRecorder'

const m = en.moves

function Harness({ initial = null, result, onChange }: { initial?: string | null; result?: GameResult; onChange?: (v: string | null) => void }) {
  const [value, setValue] = useState<string | null>(initial)
  return (
    <I18nProvider>
      <MoveRecorder
        value={value}
        onChange={(v) => {
          setValue(v)
          onChange?.(v)
        }}
        orientation="white"
        result={result}
      />
      <output data-testid="value">{value ?? ''}</output>
    </I18nProvider>
  )
}

function setup(props: Parameters<typeof Harness>[0] = {}) {
  localStorage.setItem('tf-lang', 'en')
  const utils = render(<Harness {...props} />)
  const square = (sq: string) => {
    const el = utils.container.querySelector(`[data-square="${sq}"]`)
    if (!el) throw new Error(`no square ${sq}`)
    return el as HTMLElement
  }
  const tap = (...squares: string[]) => {
    for (const sq of squares) act(() => fireEvent.click(square(sq)))
  }
  return { ...utils, square, tap, value: () => screen.getByTestId('value').textContent }
}

const moveList = () => screen.getByRole('list', { name: m.list })

describe('MoveRecorder', () => {
  // jsdom has no layout; the board measures squares to animate moves
  beforeEach(() => {
    vi.spyOn(Element.prototype, 'getBoundingClientRect').mockReturnValue({
      x: 0, y: 0, top: 0, left: 0, bottom: 40, right: 40, width: 40, height: 40, toJSON: () => ({}),
    } as DOMRect)
  })

  it('records moves by tapping a piece and then its square', () => {
    const r = setup()
    expect(screen.getByText(m.none)).toBeInTheDocument()
    r.tap('e2', 'e4')
    r.tap('e7', 'e5')
    r.tap('g1', 'f3')
    expect(r.value()).toBe('1. e4 e5 2. Nf3')
    expect(within(moveList()).getAllByRole('listitem')).toHaveLength(2)
    expect(screen.getByText(m.blackToMove)).toBeInTheDocument()
  })

  it('ignores illegal taps and pieces of the side not to move', () => {
    const r = setup()
    r.tap('e2', 'e5') // pawn can't go three squares
    r.tap('e7', 'e5') // black can't start
    r.tap('g1', 'g3') // knight can't move like that
    expect(r.value()).toBe('')
    r.tap('e2', 'e4')
    expect(r.value()).toBe('1. e4')
  })

  it('shows where the selected piece can go', () => {
    const r = setup()
    r.tap('g1')
    const target = (sq: string) => r.square(sq).querySelector('[data-legal-target]')
    expect(target('f3')).not.toBeNull()
    expect(target('h3')).not.toBeNull()
    expect(target('g3')).toBeNull()
  })

  it('undoes and clears', async () => {
    const r = setup()
    r.tap('d2', 'd4', 'd7', 'd5')
    await userEvent.click(screen.getByRole('button', { name: m.undo }))
    expect(r.value()).toBe('1. d4')
    await userEvent.click(screen.getByRole('button', { name: m.clear }))
    expect(r.value()).toBe('')
    expect(screen.getByRole('button', { name: m.undo })).toBeDisabled()
  })

  it('asks which piece a pawn becomes', async () => {
    const r = setup({ initial: '1. h4 g5 2. hxg5 f6 3. gxf6 Nc6 4. fxe7 Nf6' })
    r.tap('e7', 'f8')
    expect(r.value()).toBe('1. h4 g5 2. hxg5 f6 3. gxf6 Nc6 4. fxe7 Nf6') // nothing until a piece is chosen
    const picker = screen.getByRole('group', { name: m.promoteTo })
    await userEvent.click(within(picker).getByRole('button', { name: m.knight }))
    expect(r.value()).toBe('1. h4 g5 2. hxg5 f6 3. gxf6 Nc6 4. fxe7 Nf6 5. exf8=N')
  })

  it('takes typed or pasted moves and checks them', async () => {
    const onChange = vi.fn()
    const r = setup({ onChange })
    await userEvent.click(screen.getByRole('radio', { name: m.text }))
    const box = screen.getByLabelText(m.textLabel)
    await userEvent.type(box, '1.e4 c5 2.Nf3 d6 3.d4 cxd4')
    await userEvent.click(screen.getByRole('button', { name: m.apply }))
    expect(r.value()).toBe('1. e4 c5 2. Nf3 d6 3. d4 cxd4')
    expect(onChange).toHaveBeenLastCalledWith('1. e4 c5 2. Nf3 d6 3. d4 cxd4')
  })

  it('points at the first illegal typed move and can keep the moves before it', async () => {
    const r = setup()
    await userEvent.click(screen.getByRole('radio', { name: m.text }))
    await userEvent.type(screen.getByLabelText(m.textLabel), 'e4 e5 Nf3 Nf6 Bb5 Ke3')
    await userEvent.click(screen.getByRole('button', { name: m.apply }))
    expect(screen.getByRole('alert')).toHaveTextContent(m.illegal('3...', 'Ke3'))
    expect(r.value()).toBe('')
    await userEvent.click(screen.getByRole('button', { name: m.keepValid(5) }))
    expect(r.value()).toBe('1. e4 e5 2. Nf3 Nf6 3. Bb5')
  })

  it('opens saved free text in the text box instead of losing it', () => {
    setup({ initial: 'we played the Italian, I won on move 30' })
    expect(screen.getByText(m.unreadable)).toBeInTheDocument()
    expect(screen.getByLabelText(m.textLabel)).toHaveValue('we played the Italian, I won on move 30')
  })

  it('notices checkmate and warns when it contradicts the result', () => {
    setup({ initial: '1. f3 e5 2. g4 Qh4#', result: 'WHITE_WIN' })
    expect(screen.getByText(m.checkmate(en.common.black))).toBeInTheDocument()
    expect(screen.getByText(/but the result is/)).toBeInTheDocument()
  })

  it('says nothing about the result when it matches', () => {
    setup({ initial: '1. f3 e5 2. g4 Qh4#', result: 'BLACK_WIN' })
    expect(screen.queryByText(/but the result is/)).not.toBeInTheDocument()
  })

  it('flips the board', async () => {
    const r = setup()
    const before = r.container.querySelector('[data-square]')!.getAttribute('data-square')
    await userEvent.click(screen.getByRole('button', { name: m.flip }))
    const after = r.container.querySelector('[data-square]')!.getAttribute('data-square')
    expect(after).not.toBe(before)
  })
})
