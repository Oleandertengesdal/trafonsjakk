import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/client'
import { gamesApi } from '../../api/endpoints'
import { en } from '../../i18n/en'
import { I18nProvider } from '../../i18n/I18nProvider'
import { pendingGame } from '../../test/fixtures'
import { ToastProvider } from '../ui/Toast'
import { GameActions } from './GameActions'

vi.mock('../../api/endpoints', () => ({
  gamesApi: { confirm: vi.fn(), dispute: vi.fn(), withdraw: vi.fn() },
}))
vi.mock('../../api/queryClient', () => ({ invalidateResults: vi.fn(async () => {}) }))

const t = en.gameActions

function renderActions(game = pendingGame()) {
  localStorage.setItem('tf-lang', 'en')
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { mutations: { retry: false } } })}>
      <I18nProvider>
        <ToastProvider>
          <GameActions game={game} />
        </ToastProvider>
      </I18nProvider>
    </QueryClientProvider>,
  )
}

describe('GameActions', () => {
  // Braces matter: a function returned from beforeEach runs as a teardown hook.
  beforeEach(() => {
    vi.mocked(gamesApi.confirm).mockReset()
    vi.mocked(gamesApi.dispute).mockReset()
  })

  it('renders nothing for someone who cannot act', () => {
    renderActions(pendingGame())
    expect(screen.queryAllByRole('button')).toHaveLength(0)
  })

  it('offers the reporter only to withdraw', () => {
    renderActions(pendingGame({ canWithdraw: true }))
    expect(screen.getByRole('button', { name: t.withdraw })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: t.confirm })).not.toBeInTheDocument()
  })

  it('lets the opponent confirm', async () => {
    vi.mocked(gamesApi.confirm).mockResolvedValue(pendingGame({ status: 'CONFIRMED', ratingChange: { white: { before: 1500, after: 1662 }, black: { before: 1500, after: 1338 } } }))
    renderActions(pendingGame({ canConfirm: true, canDispute: true }))
    expect(screen.queryByRole('button', { name: t.withdraw })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: t.confirm }))
    expect(gamesApi.confirm).toHaveBeenCalledWith(42)
    expect(await screen.findByText(t.confirmedRated)).toBeInTheDocument()
  })

  it('shows a translated error when confirming fails', async () => {
    vi.mocked(gamesApi.confirm).mockRejectedValue(new ApiError(409, 'x', { code: 'game.wrong_status', params: { status: 'CONFIRMED' } }))
    renderActions(pendingGame({ canConfirm: true, canDispute: true }))
    await userEvent.click(screen.getByRole('button', { name: t.confirm }))
    expect(await screen.findByText(en.errors.codes['game.wrong_status'] as string)).toBeInTheDocument()
  })

  it('needs a real reason to dispute', async () => {
    vi.mocked(gamesApi.dispute).mockResolvedValue(pendingGame({ status: 'DISPUTED' }))
    renderActions(pendingGame({ canConfirm: true, canDispute: true }))
    await userEvent.click(screen.getByRole('button', { name: t.dispute }))
    const submit = screen.getByRole('button', { name: t.disputeSubmit })
    const reason = screen.getByLabelText(t.disputeLabel)

    await userEvent.type(reason, '  no ')
    expect(submit).toBeDisabled()
    await userEvent.type(reason, 'pe, I won ')
    expect(submit).toBeEnabled()
    await userEvent.click(submit)
    await waitFor(() => expect(gamesApi.dispute).toHaveBeenCalledWith(42, 'no pe, I won'))
    expect(await screen.findByText(t.disputed)).toBeInTheDocument()
  })
})
