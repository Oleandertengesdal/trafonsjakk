import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { I18nProvider } from '../i18n/I18nProvider'
import { RequireAdmin, RequireAuth } from './RequireAuth'

const auth = vi.hoisted(() => ({ value: { status: 'loading', me: null as null | { role: string } } }))
vi.mock('./AuthProvider', () => ({ useAuth: () => auth.value }))

function LoginProbe() {
  const location = useLocation()
  return <p>login page, from {(location.state as { from?: string } | null)?.from}</p>
}

function renderAt(url: string) {
  render(
    <I18nProvider>
      <MemoryRouter initialEntries={[url]}>
        <Routes>
          <Route path="/login" element={<LoginProbe />} />
          <Route path="/" element={<p>home</p>} />
          <Route path="/games" element={<RequireAuth><p>secret games</p></RequireAuth>} />
          <Route path="/admin" element={<RequireAuth><RequireAdmin><p>admin tools</p></RequireAdmin></RequireAuth>} />
        </Routes>
      </MemoryRouter>
    </I18nProvider>,
  )
}

describe('route guards', () => {
  it('shows a splash while the session is being checked, not the page or the login form', () => {
    auth.value = { status: 'loading', me: null }
    renderAt('/games')
    expect(screen.getByRole('status')).toBeInTheDocument()
    expect(screen.queryByText('secret games')).not.toBeInTheDocument()
    expect(screen.queryByText(/login page/)).not.toBeInTheDocument()
  })

  it('sends visitors to the login page and remembers where they were going', () => {
    auth.value = { status: 'anonymous', me: null }
    renderAt('/games?player=kari')
    expect(screen.getByText('login page, from /games?player=kari')).toBeInTheDocument()
    expect(screen.queryByText('secret games')).not.toBeInTheDocument()
  })

  it('lets members in but keeps them out of admin pages', () => {
    auth.value = { status: 'authenticated', me: { role: 'USER' } }
    renderAt('/games')
    expect(screen.getByText('secret games')).toBeInTheDocument()
    document.body.innerHTML = ''
    renderAt('/admin')
    expect(screen.queryByText('admin tools')).not.toBeInTheDocument()
    expect(screen.getByText('home')).toBeInTheDocument()
  })

  it('lets admins into admin pages', () => {
    auth.value = { status: 'authenticated', me: { role: 'ADMIN' } }
    renderAt('/admin')
    expect(screen.getByText('admin tools')).toBeInTheDocument()
  })
})
