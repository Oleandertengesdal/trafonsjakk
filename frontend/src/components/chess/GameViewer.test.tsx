import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { en } from '../../i18n/en'
import { I18nProvider } from '../../i18n/I18nProvider'
import GameViewer from './GameViewer'

const v = en.viewer
const MOVES = '1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Bxc6 dxc6 5. O-O'

function setup(moves = MOVES) {
  localStorage.setItem('tf-lang', 'en')
  const utils = render(
    <I18nProvider>
      <GameViewer moves={moves} orientation="white" whiteName="Kari" blackName="Ola" />
    </I18nProvider>,
  )
  const status = () => screen.getByTestId('viewer-status').textContent
  const current = () => screen.getByRole('list', { name: en.moves.list }).querySelector('[aria-current="true"]')?.textContent
  const piece = (sq: string) => utils.container.querySelector(`[data-square="${sq}"] [data-piece]`)?.getAttribute('data-piece')
  return { ...utils, status, current, piece }
}

describe('GameViewer', () => {
  beforeEach(() => {
    vi.spyOn(Element.prototype, 'getBoundingClientRect').mockReturnValue({
      x: 0, y: 0, top: 0, left: 0, bottom: 40, right: 40, width: 40, height: 40, toJSON: () => ({}),
    } as DOMRect)
  })

  it('opens at the final position with the last move marked', () => {
    const r = setup()
    expect(r.status()).toBe(v.afterMove('5. O-O'))
    expect(r.current()).toBe('O-O')
    expect(r.piece('g1')).toBe('wK')
  })

  it('steps back and forward with the buttons', async () => {
    const r = setup()
    await userEvent.click(screen.getByRole('button', { name: v.prev }))
    expect(r.status()).toBe(v.afterMove('4... dxc6'))
    await waitFor(() => expect(r.piece('e1')).toBe('wK')) // after the move animation
    await userEvent.click(screen.getByRole('button', { name: v.start }))
    expect(r.status()).toBe(v.startPosition)
    expect(screen.getByRole('button', { name: v.prev })).toBeDisabled()
    await waitFor(() => expect(r.piece('e2')).toBe('wP'))
    await userEvent.click(screen.getByRole('button', { name: v.next }))
    expect(r.status()).toBe(v.afterMove('1. e4'))
    await userEvent.click(screen.getByRole('button', { name: v.end }))
    expect(screen.getByRole('button', { name: v.next })).toBeDisabled()
  })

  it('jumps to a move when you tap it', async () => {
    const r = setup()
    const list = screen.getByRole('list', { name: en.moves.list })
    await userEvent.click(within(list).getByRole('button', { name: 'Bb5' }))
    expect(r.status()).toBe(v.afterMove('3. Bb5'))
    await waitFor(() => expect(r.piece('b5')).toBe('wB'))
  })

  it('follows the arrow keys, Home and End', () => {
    const r = setup()
    const viewer = screen.getByRole('group', { name: v.title })
    act(() => fireEvent.keyDown(viewer, { key: 'Home' }))
    expect(r.status()).toBe(v.startPosition)
    act(() => fireEvent.keyDown(viewer, { key: 'ArrowRight' }))
    act(() => fireEvent.keyDown(viewer, { key: 'ArrowRight' }))
    expect(r.status()).toBe(v.afterMove('1... e5'))
    act(() => fireEvent.keyDown(viewer, { key: 'ArrowLeft' }))
    expect(r.status()).toBe(v.afterMove('1. e4'))
    act(() => fireEvent.keyDown(viewer, { key: 'End' }))
    expect(r.status()).toBe(v.afterMove('5. O-O'))
  })

  it('shows the game in numbers', () => {
    setup()
    const stats = screen.getByRole('table', { name: v.statsTitle })
    expect(stats).toHaveTextContent(v.length(5))
    const captures = within(stats).getByRole('row', { name: new RegExp(v.captures) })
    expect(within(captures).getAllByRole('cell').map((c) => c.textContent)).toEqual(['1', '1'])
    const castled = within(stats).getByRole('row', { name: new RegExp(v.castled) })
    expect(within(castled).getAllByRole('cell').map((c) => c.textContent)).toEqual([v.castledShort, v.castledNone])
  })

  it('mentions checkmate', () => {
    setup('1. f3 e5 2. g4 Qh4#')
    expect(screen.getByRole('table', { name: v.statsTitle })).toHaveTextContent(v.endedMate)
  })
})
