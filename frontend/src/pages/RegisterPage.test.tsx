import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import { en } from '../i18n/en'
import { I18nProvider } from '../i18n/I18nProvider'
import { RegisterPage } from './RegisterPage'

// Braces in beforeEach below matter: a function returned from beforeEach runs as a teardown hook.
const register = vi.fn()
vi.mock('../auth/AuthProvider', () => ({ useAuth: () => ({ status: 'anonymous', register }) }))

const r = en.auth.register

function renderPage(url = '/register') {
  localStorage.setItem('tf-lang', 'en')
  render(
    <I18nProvider>
      <MemoryRouter initialEntries={[url]}>
        <Routes>
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/" element={<p>Home page</p>} />
        </Routes>
      </MemoryRouter>
    </I18nProvider>,
  )
  return {
    invite: screen.getByLabelText(r.inviteCode),
    name: screen.getByLabelText(r.realName),
    username: screen.getByLabelText(r.username),
    password: screen.getByLabelText(r.password),
    submit: screen.getByRole('button', { name: r.submit }),
  }
}

async function fillValid(f: ReturnType<typeof renderPage>) {
  await userEvent.type(f.name, 'Kari Nordmann')
  await userEvent.type(f.username, 'kari')
  await userEvent.type(f.password, 'correct-horse-battery')
}

describe('RegisterPage', () => {
  beforeEach(() => {
    register.mockReset()
  })

  it('takes the invite code from the link', () => {
    expect(renderPage('/register?code=K7QX4-MZP2R').invite).toHaveValue('K7QX4-MZP2R')
  })

  it('checks the form before calling the server', async () => {
    const f = renderPage()
    await userEvent.type(f.username, 'x!')
    await userEvent.type(f.password, 'short')
    await userEvent.click(f.submit)
    expect(register).not.toHaveBeenCalled()
    expect(screen.getByText(r.errUsername)).toBeInTheDocument()
    expect(screen.getByText(r.errRealName)).toBeInTheDocument()
    expect(screen.getByText(r.errPassword)).toBeInTheDocument()
    expect(screen.getByText(r.errInvite)).toBeInTheDocument()
    expect(f.username).toHaveAttribute('aria-invalid', 'true')
  })

  it('trims input and goes home after registering', async () => {
    register.mockResolvedValue(undefined)
    const f = renderPage('/register?code=%20k7qx4-mzp2r%20')
    await fillValid(f)
    await userEvent.type(f.username, ' ')
    await userEvent.click(f.submit)
    expect(register).toHaveBeenCalledWith({
      inviteCode: 'k7qx4-mzp2r',
      realName: 'Kari Nordmann',
      username: 'kari',
      password: 'correct-horse-battery',
    })
    expect(await screen.findByText('Home page')).toBeInTheDocument()
  })

  it('shows an invalid invite code next to the code field', async () => {
    register.mockRejectedValue(new ApiError(400, 'Invalid or expired invite code', { code: 'invite.invalid' }))
    const f = renderPage('/register?code=NOPE0-NOPE0')
    await fillValid(f)
    await userEvent.click(f.submit)
    expect(await screen.findByText(en.errors.codes['invite.invalid'] as string)).toBeInTheDocument()
    expect(f.invite).toHaveAttribute('aria-invalid', 'true')
  })

  it('maps server-side field errors to the fields', async () => {
    register.mockRejectedValue(
      new ApiError(400, 'Validation failed', { code: 'validation', fieldErrors: { username: 'x' }, fieldCodes: { username: 'Pattern' } }),
    )
    const f = renderPage('/register?code=AAAAA-BBBBB')
    await fillValid(f)
    await userEvent.click(f.submit)
    expect(await screen.findByText(en.errors.field.Pattern)).toBeInTheDocument()
    expect(f.username).toHaveAttribute('aria-invalid', 'true')
  })

  it('shows a too-long password message from the server', async () => {
    register.mockRejectedValue(new ApiError(400, 'Password is too long', { code: 'auth.password_too_long' }))
    const f = renderPage('/register?code=AAAAA-BBBBB')
    await fillValid(f)
    await userEvent.click(f.submit)
    expect(await screen.findByText(en.errors.codes['auth.password_too_long'] as string)).toBeInTheDocument()
  })
})
