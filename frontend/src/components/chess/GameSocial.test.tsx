import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/client'
import { gamesApi, socialApi } from '../../api/endpoints'
import type { GameComment } from '../../api/types'
import { en } from '../../i18n/en'
import { I18nProvider } from '../../i18n/I18nProvider'
import { kari, ola, pendingGame, player } from '../../test/fixtures'
import { ToastProvider } from '../ui/Toast'
import { GameComments } from './GameComments'
import { GameMoves } from './GameMoves'
import { KudosButton } from './KudosButton'

vi.mock('../../api/endpoints', () => ({
  socialApi: {
    comments: vi.fn(),
    addComment: vi.fn(),
    editComment: vi.fn(),
    deleteComment: vi.fn(),
    kudos: vi.fn(),
    giveKudos: vi.fn(),
    takeKudos: vi.fn(),
  },
  gamesApi: { updateMoves: vi.fn() },
}))
vi.mock('../../auth/AuthProvider', () => ({ useAuth: () => ({ me: { id: 1, role: 'USER' } }) }))

const s = en.social
const per = player(3, 'Per Olsen')

function wrap(ui: ReactNode) {
  localStorage.setItem('tf-lang', 'en')
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}>
      <I18nProvider>
        <ToastProvider>
          <MemoryRouter>{ui}</MemoryRouter>
        </ToastProvider>
      </I18nProvider>
    </QueryClientProvider>,
  )
}

const comment = (over: Partial<GameComment> = {}): GameComment => ({
  id: 1,
  author: per,
  body: 'Nice game',
  createdAt: new Date(Date.now() - 60_000).toISOString(),
  editedAt: null,
  canEdit: false,
  canDelete: false,
  ...over,
})

describe('KudosButton', () => {
  beforeEach(() => {
    vi.mocked(socialApi.giveKudos).mockReset()
    vi.mocked(socialApi.takeKudos).mockReset()
    vi.mocked(socialApi.kudos).mockResolvedValue({ count: 2, givenByMe: false, givers: [kari, per] })
  })

  it('gives kudos and shows the new count', async () => {
    vi.mocked(socialApi.giveKudos).mockResolvedValue({ count: 3, givenByMe: true, givers: [kari, per, ola] })
    wrap(<KudosButton game={pendingGame({ status: 'CONFIRMED', kudosCount: 2, canGiveKudos: true })} />)
    const button = screen.getByRole('button', { name: s.giveKudos })
    expect(button).toHaveAttribute('aria-pressed', 'false')
    expect(button).toHaveTextContent('2')
    await userEvent.click(button)
    expect(socialApi.giveKudos).toHaveBeenCalledWith(42)
    const pressed = await screen.findByRole('button', { name: s.takeKudos })
    expect(pressed).toHaveAttribute('aria-pressed', 'true')
    expect(pressed).toHaveTextContent('3')
  })

  it('takes kudos back', async () => {
    vi.mocked(socialApi.kudos).mockResolvedValue({ count: 2, givenByMe: true, givers: [kari, per] })
    vi.mocked(socialApi.takeKudos).mockResolvedValue({ count: 1, givenByMe: false, givers: [kari] })
    wrap(<KudosButton game={pendingGame({ status: 'CONFIRMED', kudosCount: 2, kudosGivenByMe: true, canGiveKudos: true })} />)
    await userEvent.click(screen.getByRole('button', { name: s.takeKudos }))
    expect(socialApi.takeKudos).toHaveBeenCalledWith(42)
    expect(await screen.findByRole('button', { name: s.giveKudos })).toHaveTextContent('1')
  })

  it('players see the count but cannot give kudos to their own game', async () => {
    wrap(<KudosButton game={pendingGame({ status: 'CONFIRMED', kudosCount: 2, canGiveKudos: false })} />)
    expect(screen.queryByRole('button', { name: s.giveKudos })).not.toBeInTheDocument()
    expect(screen.getByText(s.kudosCount(2))).toBeInTheDocument()
    expect(await screen.findByText(s.kudosFrom('Kari Nordmann, Per Olsen'))).toBeInTheDocument()
  })

  it('shows nothing on a game with no kudos that nobody can give', () => {
    const { container } = wrap(<KudosButton game={pendingGame({ status: 'PENDING', kudosCount: 0, canGiveKudos: false })} />)
    expect(container.querySelector('button')).toBeNull()
    expect(screen.queryByText(s.kudosCount(0))).not.toBeInTheDocument()
  })
})

describe('GameComments', () => {
  beforeEach(() => {
    vi.mocked(socialApi.comments).mockReset()
    vi.mocked(socialApi.addComment).mockReset()
    vi.mocked(socialApi.deleteComment).mockReset()
    vi.mocked(socialApi.editComment).mockReset()
  })

  it('lists comments as plain text and posts a new one', async () => {
    vi.mocked(socialApi.comments).mockResolvedValueOnce([comment({ body: '<b>bold?</b>' })]).mockResolvedValue([
      comment({ body: '<b>bold?</b>' }),
      comment({ id: 2, author: kari, body: 'Thanks!', canEdit: true, canDelete: true }),
    ])
    vi.mocked(socialApi.addComment).mockResolvedValue(comment({ id: 2, author: kari, body: 'Thanks!' }))
    const { container } = wrap(<GameComments game={pendingGame({ status: 'CONFIRMED' })} />)
    expect(await screen.findByText('<b>bold?</b>')).toBeInTheDocument()
    expect(container.querySelector('b')).toBeNull()

    const box = screen.getByLabelText(s.write)
    const post = screen.getByRole('button', { name: s.post })
    expect(post).toBeDisabled()
    await userEvent.type(box, '  Thanks!  ')
    await userEvent.click(post)
    expect(socialApi.addComment).toHaveBeenCalledWith(42, 'Thanks!')
    expect(await screen.findByText('Thanks!')).toBeInTheDocument()
    expect(box).toHaveValue('')
  })

  it('shows an empty state', async () => {
    vi.mocked(socialApi.comments).mockResolvedValue([])
    wrap(<GameComments game={pendingGame({ status: 'CONFIRMED' })} />)
    expect(await screen.findByText(s.noComments)).toBeInTheDocument()
  })

  it('lets you edit and delete your own comments only', async () => {
    vi.mocked(socialApi.comments).mockResolvedValue([
      comment({ id: 1, body: 'Theirs' }),
      comment({ id: 2, author: kari, body: 'Mine', canEdit: true, canDelete: true }),
    ])
    vi.mocked(socialApi.editComment).mockResolvedValue(comment({ id: 2, author: kari, body: 'Mine, edited' }))
    vi.mocked(socialApi.deleteComment).mockResolvedValue(undefined)
    wrap(<GameComments game={pendingGame({ status: 'CONFIRMED' })} />)
    const theirs = (await screen.findByText('Theirs')).closest('li')!
    expect(within(theirs).queryByRole('button', { name: s.edit })).not.toBeInTheDocument()

    const mine = screen.getByText('Mine').closest('li')!
    await userEvent.click(within(mine).getByRole('button', { name: s.edit }))
    const editBox = within(mine).getByRole('textbox')
    await userEvent.clear(editBox)
    await userEvent.type(editBox, 'Mine, edited')
    await userEvent.click(within(mine).getByRole('button', { name: s.save }))
    expect(socialApi.editComment).toHaveBeenCalledWith(42, 2, 'Mine, edited')

    await userEvent.click(within(screen.getByText('Mine').closest('li')!).getByRole('button', { name: s.delete }))
    await userEvent.click(screen.getByRole('button', { name: s.confirmDelete }))
    expect(socialApi.deleteComment).toHaveBeenCalledWith(42, 2)
  })

  it('a voided game shows old comments but no form', async () => {
    vi.mocked(socialApi.comments).mockResolvedValue([comment()])
    wrap(<GameComments game={pendingGame({ status: 'VOIDED' })} />)
    expect(await screen.findByText('Nice game')).toBeInTheDocument()
    expect(screen.queryByLabelText(s.write)).not.toBeInTheDocument()
    expect(screen.getByText(s.voided)).toBeInTheDocument()
  })

  it('keeps what you wrote if posting fails', async () => {
    vi.mocked(socialApi.comments).mockResolvedValue([])
    vi.mocked(socialApi.addComment).mockRejectedValue(new ApiError(0, 'Network error'))
    wrap(<GameComments game={pendingGame({ status: 'CONFIRMED' })} />)
    await userEvent.type(await screen.findByLabelText(s.write), 'Important thought')
    await userEvent.click(screen.getByRole('button', { name: s.post }))
    expect(await screen.findByText(en.errors.network)).toBeInTheDocument()
    expect(screen.getByLabelText(s.write)).toHaveValue('Important thought')
  })
})

describe('GameMoves', () => {
  beforeEach(() => {
    vi.mocked(gamesApi.updateMoves).mockReset()
    vi.spyOn(Element.prototype, 'getBoundingClientRect').mockReturnValue({
      x: 0, y: 0, top: 0, left: 0, bottom: 40, right: 40, width: 40, height: 40, toJSON: () => ({}),
    } as DOMRect)
  })

  it('shows stored moves as a numbered list', () => {
    wrap(<GameMoves game={pendingGame({ moves: '1. e4 e5 2. Nf3 Nc6 3. Bb5' })} />)
    const list = screen.getByRole('list', { name: en.moves.list })
    expect(within(list).getAllByRole('listitem')).toHaveLength(3)
    expect(list).toHaveTextContent('Bb5')
  })

  it('shows free text as it was written', () => {
    wrap(<GameMoves game={pendingGame({ moves: 'we played the italian' })} />)
    expect(screen.getByText('we played the italian')).toBeInTheDocument()
  })

  it('lets a player add the moves on the board and saves them', async () => {
    vi.mocked(gamesApi.updateMoves).mockResolvedValue(pendingGame({ moves: '1. e4' }))
    const { container } = wrap(<GameMoves game={pendingGame({ status: 'CONFIRMED', moves: null, canEditMoves: true })} />)
    await userEvent.click(screen.getByRole('button', { name: en.moves.add }))
    await waitFor(() => expect(container.querySelector('[data-square="e2"]')).not.toBeNull())
    await userEvent.click(container.querySelector('[data-square="e2"]')!)
    await userEvent.click(container.querySelector('[data-square="e4"]')!)
    await userEvent.click(screen.getByRole('button', { name: en.moves.save }))
    expect(gamesApi.updateMoves).toHaveBeenCalledWith(42, '1. e4')
  })

  it('hides editing from people who are not in the game', () => {
    wrap(<GameMoves game={pendingGame({ moves: null, canEditMoves: false })} />)
    expect(screen.queryByRole('button', { name: en.moves.add })).not.toBeInTheDocument()
  })
})
