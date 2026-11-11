import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import { en } from '../i18n/en'
import { I18nProvider } from '../i18n/I18nProvider'
import { nb } from '../i18n/nb'
import { LoginPage } from './LoginPage'

const login = vi.fn()
vi.mock('../auth/AuthProvider', () => ({ useAuth: () => ({ status: 'anonymous', login }) }))

function Where() {
  const l = useLocation()
  return <p>at {l.pathname + l.search}</p>
}

function renderLogin(from?: string, lang: 'en' | 'nb' = 'en') {
  localStorage.setItem('tf-lang', lang)
  const t = lang === 'en' ? en : nb
  render(
    <I18nProvider>
      <MemoryRouter initialEntries={[{ pathname: '/login', state: from === undefined ? null : { from } }]}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="*" element={<Where />} />
        </Routes>
      </MemoryRouter>
    </I18nProvider>,
  )
  return {
    username: screen.getByLabelText(t.auth.login.username),
    password: screen.getByLabelText(t.auth.login.password),
    submit: screen.getByRole('button', { name: t.auth.login.submit }),
  }
}

describe('LoginPage', () => {
  beforeEach(() => {
    login.mockReset()
  })

  it('logs in with a trimmed username and returns to the page you came from', async () => {
    login.mockResolvedValue(undefined)
    const f = renderLogin('/games?player=kari')
    await userEvent.type(f.username, '  Kari ')
    await userEvent.type(f.password, ' secret with spaces ')
    await userEvent.click(f.submit)
    expect(login).toHaveBeenCalledWith('Kari', ' secret with spaces ')
    expect(await screen.findByText('at /games?player=kari')).toBeInTheDocument()
  })

  it('goes home when there is nowhere to return to', async () => {
    login.mockResolvedValue(undefined)
    const f = renderLogin()
    await userEvent.type(f.username, 'kari')
    await userEvent.type(f.password, 'pw')
    await userEvent.click(f.submit)
    expect(await screen.findByText('at /')).toBeInTheDocument()
  })

  it.each(['https://evil.example/login', '//evil.example/x', '/\\evil.example', 'javascript:alert(1)'])(
    'never leaves the app after login (from = %s)',
    async (from) => {
      login.mockResolvedValue(undefined)
      const f = renderLogin(from)
      await userEvent.type(f.username, 'kari')
      await userEvent.type(f.password, 'pw')
      await userEvent.click(f.submit)
      expect(await screen.findByText('at /')).toBeInTheDocument()
    },
  )

  it('does nothing with empty fields', async () => {
    const f = renderLogin()
    await userEvent.click(f.submit)
    await userEvent.type(f.username, '   ')
    await userEvent.click(f.submit)
    expect(login).not.toHaveBeenCalled()
  })

  it('explains a lockout in Norwegian, with the wait time', async () => {
    login.mockRejectedValue(new ApiError(429, 'Too many', { code: 'auth.locked', params: { minutes: 15 } }))
    const f = renderLogin(undefined, 'nb')
    await userEvent.type(f.username, 'kari')
    await userEvent.type(f.password, 'wrong')
    await userEvent.click(f.submit)
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('15')
    expect(alert.textContent).toBe((nb.errors.codes['auth.locked'] as (p: Record<string, unknown>) => string)({ minutes: 15 }))
  })
})
