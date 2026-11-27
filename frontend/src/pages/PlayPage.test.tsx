import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { onlineApi, usersApi } from '../api/endpoints'
import { queryClient } from '../api/queryClient'
import type { Challenge, Lobby, OnlineGame } from '../api/types'
import { ToastProvider } from '../components/ui/Toast'
import { en } from '../i18n/en'
import { I18nProvider } from '../i18n/I18nProvider'
import { kari, ola, player } from '../test/fixtures'
import { PlayPage } from './PlayPage'

vi.mock('../api/endpoints', () => ({
  onlineApi: { lobby: vi.fn(), challenge: vi.fn(), accept: vi.fn(), decline: vi.fn(), cancel: vi.fn() },
  usersApi: { list: vi.fn() },
}))
vi.mock('../auth/AuthProvider', () => ({ useAuth: () => ({ me: { id: 1 } }) }))

const p = en.play
const per = player(3, 'Per Berg')

const lobby = (over: Partial<Lobby> = {}): Lobby => ({
  challengesToMe: [],
  myChallenges: [],
  openChallenges: [],
  myGames: [],
  myTurn: [],
  playing: [],
  recent: [],
  ...over,
})

const challenge = (over: Partial<Challenge> = {}): Challenge => ({
  id: 4,
  createdBy: ola,
  opponent: kari,
  colour: 'WHITE',
  rated: true,
  timeControl: { kind: 'LIVE', initialSeconds: 600, incrementSeconds: 5, daysPerMove: 0, label: '10+5' },
  createdAt: new Date().toISOString(),
  expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
  ...over,
})

const onlineGame = (over: Partial<OnlineGame> = {}): OnlineGame =>
  ({
    id: 12,
    white: kari,
    black: ola,
    rated: true,
    timeControl: { kind: 'LIVE', initialSeconds: 300, incrementSeconds: 3, daysPerMove: 0, label: '5+3' },
    status: 'ACTIVE',
    plies: 0,
    turn: 'WHITE',
    myColour: 'WHITE',
    fen: 'x',
    result: null,
    ...over,
  }) as OnlineGame

function renderPage() {
  localStorage.setItem('tf-lang', 'en')
  queryClient.clear()
  render(
    <QueryClientProvider client={queryClient}>
      <I18nProvider>
        <ToastProvider>
          <MemoryRouter initialEntries={['/play']}>
            <Routes>
              <Route path="/play" element={<PlayPage />} />
              <Route path="/play/:id" element={<p>board</p>} />
            </Routes>
          </MemoryRouter>
        </ToastProvider>
      </I18nProvider>
    </QueryClientProvider>,
  )
}

describe('PlayPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(usersApi.list).mockResolvedValue([kari, ola, per])
  })

  it('sends a challenge with the chosen time control, colour and rating', async () => {
    vi.mocked(onlineApi.lobby).mockResolvedValue(lobby())
    vi.mocked(onlineApi.challenge).mockResolvedValue(challenge())
    renderPage()
    const form = (await screen.findByRole('heading', { name: p.challengeTitle })).closest('section')!
    await waitFor(() => expect(within(form).getAllByRole('option').length).toBeGreaterThan(1))
    // You can't challenge yourself: you're not in the list
    expect(within(form).queryByRole('option', { name: /Kari Nordmann/ })).not.toBeInTheDocument()
    await userEvent.selectOptions(within(form).getByLabelText(p.opponent), String(per.id))
    await userEvent.click(within(form).getByRole('radio', { name: '15+10' }))
    await userEvent.click(within(form).getByRole('radio', { name: p.colours.BLACK }))
    await userEvent.click(within(form).getByRole('radio', { name: new RegExp(p.ratedOptions.casual) }))
    await userEvent.click(within(form).getByRole('button', { name: p.send }))
    expect(onlineApi.challenge).toHaveBeenCalledWith({
      opponentId: per.id,
      colour: 'BLACK',
      rated: false,
      kind: 'LIVE',
      initialSeconds: 900,
      incrementSeconds: 10,
    })
    expect(await screen.findByText(p.sent(per.realName))).toBeInTheDocument()
  })

  it('sends a daily challenge to anyone', async () => {
    vi.mocked(onlineApi.lobby).mockResolvedValue(lobby())
    vi.mocked(onlineApi.challenge).mockResolvedValue(challenge())
    renderPage()
    const form = (await screen.findByRole('heading', { name: p.challengeTitle })).closest('section')!
    await userEvent.click(within(form).getByRole('radio', { name: p.kinds.DAILY }))
    await userEvent.click(within(form).getByRole('radio', { name: p.days(7) }))
    await userEvent.click(within(form).getByRole('button', { name: p.send }))
    expect(onlineApi.challenge).toHaveBeenCalledWith({ opponentId: null, colour: 'RANDOM', rated: true, kind: 'DAILY', daysPerMove: 7 })
    expect(await screen.findByText(p.sentOpen)).toBeInTheDocument()
  })

  it('accepts a challenge and goes to the board', async () => {
    vi.mocked(onlineApi.lobby).mockResolvedValue(lobby({ challengesToMe: [challenge()] }))
    vi.mocked(onlineApi.accept).mockResolvedValue(onlineGame({ id: 12 }))
    renderPage()
    const row = (await screen.findByText(p.challengedYou(ola.realName))).closest('li')!
    // Ola plays white, so Kari (who's looking) gets black
    expect(within(row).getByText(new RegExp(p.youPlay(p.colours.BLACK)))).toBeInTheDocument()
    await userEvent.click(within(row).getByRole('button', { name: p.accept }))
    expect(onlineApi.accept).toHaveBeenCalledWith(4)
    expect(await screen.findByText('board')).toBeInTheDocument()
  })

  it('lists your games with your move first, and lets you withdraw a challenge', async () => {
    const waiting = onlineGame({ id: 20, turn: 'BLACK' })
    const mine = onlineGame({ id: 21, timeControl: { kind: 'DAILY', initialSeconds: 0, incrementSeconds: 0, daysPerMove: 3, label: '3d' } })
    vi.mocked(onlineApi.lobby).mockResolvedValue(
      lobby({ myGames: [waiting, mine], myTurn: [mine], playing: [waiting, mine], myChallenges: [challenge({ createdBy: kari, opponent: per })] }),
    )
    vi.mocked(onlineApi.cancel).mockResolvedValue()
    renderPage()
    const games = (await screen.findByRole('heading', { name: p.myGames })).closest('section')!
    const links = within(games).getAllByRole('link')
    expect(links[0]).toHaveAttribute('href', '/play/21')
    expect(within(links[0]).getByText(p.yourTurn)).toBeInTheDocument()
    expect(links[1]).toHaveAttribute('href', '/play/20')
    expect(screen.getByText(p.nobodyPlaying)).toBeInTheDocument()

    const row = screen.getByText(p.waitingFor(per.realName)).closest('li')!
    await userEvent.click(within(row).getByRole('button', { name: p.withdraw }))
    expect(onlineApi.cancel).toHaveBeenCalledWith(4)
  })

  it('goes to the board when someone takes your challenge', async () => {
    vi.mocked(onlineApi.lobby)
      .mockResolvedValueOnce(lobby({ myChallenges: [challenge({ createdBy: kari, opponent: null })] }))
      .mockResolvedValue(lobby({ myGames: [onlineGame({ id: 33 })] }))
    renderPage()
    await screen.findByText(p.openToAnyone)
    await queryClient.refetchQueries({ queryKey: ['online', 'lobby'] })
    expect(await screen.findByText('board')).toBeInTheDocument()
  })
})
