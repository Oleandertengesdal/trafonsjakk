import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import { usersApi } from '../api/endpoints'
import { queryClient } from '../api/queryClient'
import { ToastProvider } from '../components/ui/Toast'
import { en } from '../i18n/en'
import { I18nProvider } from '../i18n/I18nProvider'
import { SettingsPage } from './SettingsPage'

vi.mock('../api/endpoints', () => ({
  usersApi: { updateMe: vi.fn(), changePassword: vi.fn(), exportData: vi.fn(), deleteAccount: vi.fn() },
}))
const auth = vi.hoisted(() => ({ logout: vi.fn() }))
vi.mock('../auth/AuthProvider', () => ({
  useAuth: () => ({ me: { id: 1, username: 'kari', realName: 'Kari Nordmann' }, setMe: vi.fn(), logout: auth.logout }),
}))

const st = en.settings

function renderPage() {
  localStorage.setItem('tf-lang', 'en')
  queryClient.clear()
  render(
    <QueryClientProvider client={queryClient}>
      <I18nProvider>
        <ToastProvider>
          <MemoryRouter>
            <SettingsPage />
          </MemoryRouter>
        </ToastProvider>
      </I18nProvider>
    </QueryClientProvider>,
  )
}

describe('SettingsPage: your data', () => {
  beforeEach(() => vi.resetAllMocks())

  it('downloads everything as a JSON file', async () => {
    vi.mocked(usersApi.exportData).mockResolvedValue({ account: { username: 'kari' } })
    const createObjectURL = vi.fn().mockReturnValue('blob:data')
    Object.assign(URL, { createObjectURL, revokeObjectURL: vi.fn() })
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    renderPage()
    await userEvent.click(screen.getByRole('button', { name: st.download }))
    expect(await screen.findByText(st.downloaded)).toBeInTheDocument()
    expect(createObjectURL).toHaveBeenCalled()
    expect((click.mock.contexts[0] as HTMLAnchorElement).download).toBe('trafonsjakk-kari.json')
  })

  it('deletes the account only with the password, then logs out', async () => {
    vi.mocked(usersApi.deleteAccount)
      .mockRejectedValueOnce(new ApiError(400, 'wrong', { code: 'auth.wrong_current_password' }))
      .mockResolvedValue(undefined)
    renderPage()
    await userEvent.click(screen.getByRole('button', { name: st.deleteButton }))
    const confirm = screen.getByRole('button', { name: st.deleteFinal })
    expect(confirm).toBeDisabled()

    await userEvent.type(screen.getByLabelText(st.deletePassword), 'guess')
    await userEvent.click(confirm)
    expect(await screen.findByText(en.errors.codes['auth.wrong_current_password'] as string)).toBeInTheDocument()
    expect(auth.logout).not.toHaveBeenCalled()

    await userEvent.clear(screen.getByLabelText(st.deletePassword))
    await userEvent.type(screen.getByLabelText(st.deletePassword), 'my real password')
    await userEvent.click(confirm)
    await waitFor(() => expect(auth.logout).toHaveBeenCalled())
    expect(usersApi.deleteAccount).toHaveBeenLastCalledWith('my real password')
  })
})
