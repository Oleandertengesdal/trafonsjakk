import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import { authApi } from '../api/endpoints'
import { en } from '../i18n/en'
import { I18nProvider } from '../i18n/I18nProvider'
import { ResetPasswordPage } from './ResetPasswordPage'

vi.mock('../api/endpoints', () => ({ authApi: { checkReset: vi.fn(), resetPassword: vi.fn() } }))
const setMe = vi.fn()
vi.mock('../auth/AuthProvider', () => ({ useAuth: () => ({ status: 'anonymous', setMe }) }))

const r = en.reset

function Home() {
  const l = useLocation()
  return <p>home {l.search}</p>
}

function renderPage(url: string) {
  localStorage.setItem('tf-lang', 'en')
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <I18nProvider>
        <MemoryRouter initialEntries={[url]}>
          <Routes>
            <Route path="/reset-password" element={<ResetPasswordPage />} />
            <Route path="/" element={<Home />} />
          </Routes>
        </MemoryRouter>
      </I18nProvider>
    </QueryClientProvider>,
  )
}

describe('ResetPasswordPage', () => {
  beforeEach(() => {
    vi.mocked(authApi.checkReset).mockReset()
    vi.mocked(authApi.resetPassword).mockReset()
    setMe.mockReset()
  })

  it('shows whose password it is, checks the fields, then logs in and goes home', async () => {
    vi.mocked(authApi.checkReset).mockResolvedValue({ username: 'ola', realName: 'Ola Hansen', expiresAt: '2026-11-12T12:00:00Z' })
    vi.mocked(authApi.resetPassword).mockResolvedValue({ id: 2, username: 'ola' } as never)
    renderPage('/reset-password?token=abc')
    expect(await screen.findByText(r.subtitle('Ola Hansen', 'ola'))).toBeInTheDocument()
    expect(authApi.checkReset).toHaveBeenCalledWith('abc')

    const pw = screen.getByLabelText(r.newPassword)
    const again = screen.getByLabelText(r.repeat)
    const submit = screen.getByRole('button', { name: r.submit })
    await userEvent.type(pw, 'short')
    await userEvent.click(submit)
    expect(screen.getByText(r.tooShort)).toBeInTheDocument()
    await userEvent.clear(pw)
    await userEvent.type(pw, 'a-good-long-passphrase')
    await userEvent.type(again, 'a-good-long-passphrasE')
    await userEvent.click(submit)
    expect(screen.getByText(r.mismatch)).toBeInTheDocument()
    expect(authApi.resetPassword).not.toHaveBeenCalled()

    await userEvent.clear(again)
    await userEvent.type(again, 'a-good-long-passphrase')
    await userEvent.click(submit)
    expect(authApi.resetPassword).toHaveBeenCalledWith('abc', 'a-good-long-passphrase')
    expect(await screen.findByText('home')).toBeInTheDocument() // token gone from the address
    expect(setMe).toHaveBeenCalledWith({ id: 2, username: 'ola' })
  })

  it('explains a used or expired link', async () => {
    vi.mocked(authApi.checkReset).mockRejectedValue(new ApiError(400, 'x', { code: 'reset.invalid' }))
    renderPage('/reset-password?token=old')
    expect(await screen.findByText(r.invalidTitle)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: r.toLogin })).toHaveAttribute('href', '/login')
  })

  it('handles a link without a token', async () => {
    renderPage('/reset-password')
    expect(await screen.findByText(r.invalidTitle)).toBeInTheDocument()
    expect(authApi.checkReset).not.toHaveBeenCalled()
  })

  it('shows the server telling it the link was just used elsewhere', async () => {
    vi.mocked(authApi.checkReset).mockResolvedValue({ username: 'ola', realName: 'Ola Hansen', expiresAt: '2026-11-12T12:00:00Z' })
    vi.mocked(authApi.resetPassword).mockRejectedValue(new ApiError(400, 'x', { code: 'reset.invalid' }))
    renderPage('/reset-password?token=abc')
    await userEvent.type(await screen.findByLabelText(r.newPassword), 'a-good-long-passphrase')
    await userEvent.type(screen.getByLabelText(r.repeat), 'a-good-long-passphrase')
    await userEvent.click(screen.getByRole('button', { name: r.submit }))
    expect(await screen.findByText(r.invalidTitle)).toBeInTheDocument()
  })
})
