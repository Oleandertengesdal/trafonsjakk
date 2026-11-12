import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import { liveApi } from '../api/endpoints'
import { queryClient } from '../api/queryClient'
import type { LiveGame } from '../api/types'
import { ToastProvider } from '../components/ui/Toast'
import { en } from '../i18n/en'
import { I18nProvider } from '../i18n/I18nProvider'
import { kari, ola, pendingGame } from '../test/fixtures'
import { LiveGamePage } from './LiveGamePage'

vi.mock('../api/endpoints', () => ({
  liveApi: { get: vi.fn(), saveMoves: vi.fn(), finish: vi.fn(), abandon: vi.fn() },
}))
vi.mock('../api/queryClient', async (orig) => ({ ...(await orig<object>()), invalidateResults: vi.fn() }))
vi.mock('../auth/AuthProvider', () => ({ useAuth: () => ({ me: { id: 1 } }) }))

const l = en.live
const m = en.moves

const live = (over: Partial<LiveGame> = {}): LiveGame => ({
  id: 7,
  white: kari,
  black: ola,
  status: 'LIVE',
  moves: null,
  version: 0,
  startedAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
  gameId: null,
  canRecord: true,
  ...over,
})

function renderPage() {
  localStorage.setItem('tf-lang', 'en')
  queryClient.clear()
  render(
    <QueryClientProvider client={queryClient}>
      <I18nProvider>
        <ToastProvider>
          <MemoryRouter initialEntries={['/live/7']}>
            <Routes>
              <Route path="/live/:id" element={<LiveGamePage />} />
              <Route path="/games/:id" element={<p>game page</p>} />
            </Routes>
          </MemoryRouter>
        </ToastProvider>
      </I18nProvider>
    </QueryClientProvider>,
  )
}

async function typeMoves(text: string) {
  await userEvent.click(await screen.findByRole('radio', { name: m.text }))
  const box = screen.getByLabelText(m.textLabel)
  await userEvent.clear(box)
  await userEvent.type(box, text)
  await userEvent.click(screen.getByRole('button', { name: m.apply }))
}

describe('LiveGamePage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.spyOn(Element.prototype, 'getBoundingClientRect').mockReturnValue({
      x: 0, y: 0, top: 0, left: 0, bottom: 40, right: 40, width: 40, height: 40, toJSON: () => ({}),
    } as DOMRect)
  })

  it('saves each change for a player and logs the result at the end', async () => {
    vi.mocked(liveApi.get).mockResolvedValue(live())
    vi.mocked(liveApi.saveMoves).mockImplementation(async (_id, moves, version) => live({ moves, version: version + 1 }))
    vi.mocked(liveApi.finish).mockResolvedValue(pendingGame({ id: 42 }))
    renderPage()
    expect(await screen.findByText(l.youRecord)).toBeInTheDocument()

    await typeMoves('e4 e5')
    await waitFor(() => expect(liveApi.saveMoves).toHaveBeenCalledWith(7, '1. e4 e5', 0))
    expect(await screen.findByText(l.saved)).toBeInTheDocument()
    await typeMoves('e4 e5 Nf3')
    await waitFor(() => expect(liveApi.saveMoves).toHaveBeenLastCalledWith(7, '1. e4 e5 2. Nf3', 1))

    await userEvent.click(screen.getByRole('button', { name: /1–0/ }))
    expect(screen.getByText(l.finishConfirm(en.result.WHITE_WIN))).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: l.finish }))
    expect(liveApi.finish).toHaveBeenCalledWith(7, 'WHITE_WIN')
    expect(await screen.findByText('game page')).toBeInTheDocument()
    expect(screen.getByText(l.finished(ola.realName))).toBeInTheDocument()
  })

  it('takes the other phone’s moves when it saved first', async () => {
    vi.mocked(liveApi.get).mockResolvedValueOnce(live()).mockResolvedValue(live({ moves: '1. d4', version: 1 }))
    vi.mocked(liveApi.saveMoves).mockRejectedValue(new ApiError(409, 'stale', { code: 'live.out_of_date' }))
    renderPage()
    await typeMoves('e4')
    expect(await screen.findByText(l.updatedElsewhere)).toBeInTheDocument()
    // The recorder starts over from the saved moves, on the board
    expect(within(screen.getByRole('list', { name: m.list })).getByText('d4')).toBeInTheDocument()
  })

  it('lets everyone else follow without recording', async () => {
    vi.mocked(liveApi.get).mockResolvedValue(live({ canRecord: false, moves: '1. e4 c5' }))
    renderPage()
    expect(await screen.findByText(l.following)).toBeInTheDocument()
    expect(screen.getByText('1. e4 c5')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /1–0/ })).not.toBeInTheDocument()
  })

  it('points to the logged game once it is over', async () => {
    vi.mocked(liveApi.get).mockResolvedValue(live({ status: 'FINISHED', gameId: 42 }))
    renderPage()
    expect(await screen.findByText(l.endedTitle)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: l.openGame })).toHaveAttribute('href', '/games/42')
  })
})
