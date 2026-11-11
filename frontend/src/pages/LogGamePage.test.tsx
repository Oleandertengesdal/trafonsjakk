import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { gamesApi, tournamentsApi, usersApi } from '../api/endpoints'
import type { MyMatch } from '../api/types'
import { ToastProvider } from '../components/ui/Toast'
import { en } from '../i18n/en'
import { I18nProvider } from '../i18n/I18nProvider'
import { toLocalInput } from '../i18n/format'
import { kari, ola, pendingGame, player } from '../test/fixtures'
import { LogGamePage } from './LogGamePage'

vi.mock('../api/endpoints', () => ({
  usersApi: { list: vi.fn() },
  tournamentsApi: { myMatches: vi.fn() },
  gamesApi: { create: vi.fn() },
}))
vi.mock('../api/queryClient', () => ({ invalidateResults: vi.fn(async () => {}) }))
const auth = vi.hoisted(() => ({ me: { id: 1, role: 'USER' } as { id: number; role: string } }))
vi.mock('../auth/AuthProvider', () => ({ useAuth: () => ({ me: auth.me }) }))

const l = en.logGame
const per = player(3, 'Per Olsen')

function renderForm(url = '/log') {
  localStorage.setItem('tf-lang', 'en')
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}>
      <I18nProvider>
        <ToastProvider>
          <MemoryRouter initialEntries={[url]}>
            <Routes>
              <Route path="/log" element={<LogGamePage />} />
              <Route path="/games/:id" element={<p>game page</p>} />
            </Routes>
          </MemoryRouter>
        </ToastProvider>
      </I18nProvider>
    </QueryClientProvider>,
  )
}

const submit = () => userEvent.click(screen.getByRole('button', { name: l.submit }))
const sent = () => vi.mocked(gamesApi.create).mock.calls[0][0]

async function chooseOpponent(p = ola) {
  const select = await screen.findByLabelText(l.opponent)
  await userEvent.selectOptions(select, String(p.id))
}

describe('LogGamePage', () => {
  beforeEach(() => {
    auth.me = { id: 1, role: 'USER' }
    vi.mocked(usersApi.list).mockResolvedValue([kari, ola, per])
    vi.mocked(tournamentsApi.myMatches).mockResolvedValue([])
    vi.mocked(gamesApi.create).mockReset()
    vi.mocked(gamesApi.create).mockResolvedValue(pendingGame())
  })

  it("doesn't offer yourself as an opponent", async () => {
    renderForm()
    const select = await screen.findByLabelText(l.opponent)
    const options = within(select).getAllByRole('option').map((o) => o.getAttribute('value'))
    expect(options).toEqual(['', '2', '3'])
  })

  it.each([
    ['WHITE', l.iWon, 'WHITE_WIN', 1, 2],
    ['WHITE', l.iLost, 'BLACK_WIN', 1, 2],
    ['BLACK', l.iWon, 'BLACK_WIN', 2, 1],
    ['BLACK', l.iLost, 'WHITE_WIN', 2, 1],
    ['BLACK', l.draw, 'DRAW', 2, 1],
  ])('playing %s, "%s" is sent as %s', async (colour, outcome, result, whiteId, blackId) => {
    renderForm()
    await chooseOpponent()
    await userEvent.click(screen.getByRole('radio', { name: colour === 'WHITE' ? en.common.white : en.common.black }))
    await userEvent.click(screen.getByRole('radio', { name: outcome }))
    await submit()
    await waitFor(() => expect(gamesApi.create).toHaveBeenCalledTimes(1))
    expect(sent()).toMatchObject({ whiteId, blackId, result })
    expect(sent().matchId).toBeUndefined()
    expect(await screen.findByText('game page')).toBeInTheDocument()
  })

  it('needs an opponent', async () => {
    renderForm()
    await screen.findByLabelText(l.opponent)
    await submit()
    expect(screen.getByText(l.errOpponent)).toBeInTheDocument()
    expect(gamesApi.create).not.toHaveBeenCalled()
  })

  it('refuses a time in the future but allows a slightly fast clock', async () => {
    renderForm()
    await chooseOpponent()
    const when = screen.getByLabelText(l.when)
    await userEvent.clear(when)
    await userEvent.type(when, toLocalInput(new Date(Date.now() + 60 * 60_000)))
    await submit()
    expect(screen.getByText(l.errFuture)).toBeInTheDocument()
    expect(gamesApi.create).not.toHaveBeenCalled()

    await userEvent.clear(when)
    await userEvent.type(when, toLocalInput(new Date(Date.now() + 5 * 60_000)))
    await submit()
    await waitFor(() => expect(gamesApi.create).toHaveBeenCalledTimes(1))
  })

  it('checks the ECO code and sends optional text trimmed', async () => {
    renderForm()
    await chooseOpponent()
    await userEvent.click(screen.getByText(l.details))
    await userEvent.type(screen.getByLabelText(l.eco), 'z9')
    await submit()
    expect(screen.getByText(l.errEco)).toBeInTheDocument()

    await userEvent.clear(screen.getByLabelText(l.eco))
    await userEvent.type(screen.getByLabelText(l.eco), 'c50')
    await userEvent.type(screen.getByLabelText(l.opening), '  Italian Game  ')
    await userEvent.type(screen.getByLabelText(l.notes), '   ')
    await submit()
    await waitFor(() => expect(gamesApi.create).toHaveBeenCalledTimes(1))
    expect(sent()).toMatchObject({ eco: 'C50', opening: 'Italian Game' })
    expect(sent().notes).toBeUndefined()
  })

  const match: MyMatch = {
    matchId: 77,
    tournamentId: 5,
    tournamentName: 'Vårcupen',
    format: 'SINGLE_ELIMINATION',
    round: 2,
    totalRounds: 2,
    roundName: 'Final',
    white: ola,
    black: kari,
    needsReplay: false,
  }

  it('fills in a tournament game from the link, with the colours the server offers', async () => {
    vi.mocked(tournamentsApi.myMatches).mockResolvedValue([match])
    renderForm('/log?match=77')
    expect(await screen.findByText(`Vårcupen, ${en.round.final}`)).toBeInTheDocument()
    expect(screen.queryByLabelText(l.opponent)).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('radio', { name: l.iWon }))
    await submit()
    await waitFor(() => expect(gamesApi.create).toHaveBeenCalledTimes(1))
    // Kari (me) had black in this pairing and won
    expect(sent()).toMatchObject({ whiteId: 2, blackId: 1, result: 'BLACK_WIN', matchId: 77 })
  })

  it('lets you swap colours if you sat the other way round', async () => {
    vi.mocked(tournamentsApi.myMatches).mockResolvedValue([match])
    renderForm('/log?match=77')
    await userEvent.click(await screen.findByRole('button', { name: l.swapColours }))
    await userEvent.click(screen.getByRole('radio', { name: l.iWon }))
    await submit()
    await waitFor(() => expect(gamesApi.create).toHaveBeenCalledTimes(1))
    expect(sent()).toMatchObject({ whiteId: 1, blackId: 2, result: 'WHITE_WIN', matchId: 77 })
  })

  it('says so when the tournament game from the link is no longer open', async () => {
    renderForm('/log?match=77')
    expect(await screen.findByText(l.matchUnavailable)).toBeInTheDocument()
    expect(await screen.findByLabelText(l.opponent)).toBeInTheDocument()
  })

  it('ignores a malformed match parameter', async () => {
    renderForm('/log?match=77abc')
    expect(await screen.findByLabelText(l.opponent)).toBeInTheDocument()
    expect(screen.queryByText(l.matchUnavailable)).not.toBeInTheDocument()
  })

  it('lets an admin log a game between two other players, with the absolute result', async () => {
    auth.me = { id: 1, role: 'ADMIN' }
    renderForm()
    await userEvent.click(await screen.findByLabelText(l.forOthers))
    await userEvent.selectOptions(screen.getByLabelText(en.common.white), '2')
    await userEvent.selectOptions(screen.getByLabelText(en.common.black), '3')
    await userEvent.click(screen.getByRole('radio', { name: new RegExp(en.result.BLACK_WIN) }))
    await submit()
    await waitFor(() => expect(gamesApi.create).toHaveBeenCalledTimes(1))
    expect(sent()).toMatchObject({ whiteId: 2, blackId: 3, result: 'BLACK_WIN' })
  })
})
