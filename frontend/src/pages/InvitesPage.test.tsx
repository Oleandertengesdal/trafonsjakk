import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { invitesApi } from '../api/endpoints'
import { queryClient } from '../api/queryClient'
import type { Invite } from '../api/types'
import { ToastProvider } from '../components/ui/Toast'
import { en } from '../i18n/en'
import { I18nProvider } from '../i18n/I18nProvider'
import { kari, ola, player } from '../test/fixtures'
import { InvitesPage } from './InvitesPage'

vi.mock('../api/endpoints', () => ({ invitesApi: { list: vi.fn(), create: vi.fn(), revoke: vi.fn() } }))
const auth = vi.hoisted(() => ({ me: { id: 1, role: 'USER' } }))
vi.mock('../auth/AuthProvider', () => ({ useAuth: () => ({ me: auth.me }) }))

const inv = en.invites
const soon = new Date(Date.now() + 3 * 86_400_000).toISOString()

const invite = (over: Partial<Invite>): Invite => ({
  id: 1,
  createdBy: kari,
  createdAt: new Date().toISOString(),
  expiresAt: soon,
  maxUses: 1,
  useCount: 0,
  uses: [],
  status: 'ACTIVE',
  ...over,
})

function renderPage(role: 'USER' | 'ADMIN') {
  auth.me = { id: 1, role }
  localStorage.setItem('tf-lang', 'en')
  queryClient.clear()
  render(
    <QueryClientProvider client={queryClient}>
      <I18nProvider>
        <ToastProvider>
          <MemoryRouter>
            <InvitesPage />
          </MemoryRouter>
        </ToastProvider>
      </I18nProvider>
    </QueryClientProvider>,
  )
}

describe('InvitesPage', () => {
  beforeEach(() => {
    vi.mocked(invitesApi.list).mockResolvedValue([])
    vi.mocked(invitesApi.create).mockReset()
    vi.mocked(invitesApi.create).mockResolvedValue({ id: 9, code: 'K7QX4-MZP2R', expiresAt: soon, maxUses: 1 })
  })

  it('members make a single-person code with one tap', async () => {
    renderPage('USER')
    expect(screen.queryByLabelText(inv.howMany)).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: inv.create }))
    expect(invitesApi.create).toHaveBeenCalledWith({})
    expect(await screen.findByText('K7QX4-MZP2R')).toBeInTheDocument()
  })

  it('admins choose how many people and for how long', async () => {
    vi.mocked(invitesApi.create).mockResolvedValue({ id: 9, code: 'GROUP-CODE1', expiresAt: soon, maxUses: 25 })
    renderPage('ADMIN')
    const howMany = await screen.findByLabelText(inv.howMany)
    await userEvent.clear(howMany)
    await userEvent.type(howMany, '25')
    await userEvent.selectOptions(screen.getByLabelText(inv.validFor), inv.durations.d30)
    await userEvent.click(screen.getByRole('button', { name: inv.create }))
    expect(invitesApi.create).toHaveBeenCalledWith({ maxUses: 25, validHours: 720 })
    expect(await screen.findByText('GROUP-CODE1')).toBeInTheDocument()
    expect(screen.getByText(inv.forPeople(25), { exact: false })).toBeInTheDocument()
  })

  it('admins default to one person for a week', async () => {
    renderPage('ADMIN')
    await screen.findByLabelText(inv.howMany)
    await userEvent.click(screen.getByRole('button', { name: inv.create }))
    expect(invitesApi.create).toHaveBeenCalledWith({ maxUses: 1, validHours: 168 })
  })

  it('does not send an out-of-range number', async () => {
    renderPage('ADMIN')
    const howMany = await screen.findByLabelText(inv.howMany)
    await userEvent.clear(howMany)
    await userEvent.type(howMany, '600')
    await userEvent.click(screen.getByRole('button', { name: inv.create }))
    expect(invitesApi.create).not.toHaveBeenCalled()
    expect(howMany).toHaveAttribute('aria-invalid', 'true')
  })

  it('shows how far a group code has been used and who joined', async () => {
    vi.mocked(invitesApi.list).mockResolvedValue([
      invite({ id: 1, maxUses: 5, useCount: 2, uses: [{ player: ola, usedAt: soon }, { player: player(3, 'Per Olsen'), usedAt: soon }] }),
      invite({ id: 2, status: 'USED', useCount: 1, uses: [{ player: ola, usedAt: new Date().toISOString() }] }),
    ])
    renderPage('ADMIN')
    expect(await screen.findByText(inv.usedOf(2, 5), { exact: false })).toBeInTheDocument()
    expect(screen.getByText(inv.joinedList('Ola Hansen, Per Olsen'), { exact: false })).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText(new RegExp(`Ola Hansen joined`))).toBeInTheDocument())
  })
})
