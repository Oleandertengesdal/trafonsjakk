import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as client from '../api/client'
import { ApiError } from '../api/client'
import { authApi } from '../api/endpoints'
import { queryClient } from '../api/queryClient'
import type { Me } from '../api/types'
import { AuthProvider, useAuth } from './AuthProvider'

vi.mock('../api/endpoints', () => ({
  authApi: { csrf: vi.fn(async () => undefined), me: vi.fn(), login: vi.fn(), register: vi.fn(), logout: vi.fn() },
}))

const kari: Me = { id: 1, username: 'kari', realName: 'Kari Nordmann', rating: 1500, rd: 350, provisional: true, role: 'USER' } as Me

function Probe() {
  const { status, me, logout } = useAuth()
  return (
    <div>
      <p data-testid="status">{status}</p>
      <p data-testid="me">{me?.username ?? '-'}</p>
      <button onClick={() => void logout()}>out</button>
    </div>
  )
}

const status = () => screen.getByTestId('status').textContent

describe('AuthProvider', () => {
  // Braces matter: a function returned from beforeEach runs as a teardown hook.
  beforeEach(() => {
    vi.mocked(authApi.me).mockReset()
    vi.mocked(authApi.logout).mockReset()
    queryClient.clear()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('restores a session on load', async () => {
    vi.mocked(authApi.me).mockResolvedValue(kari)
    render(<AuthProvider><Probe /></AuthProvider>)
    expect(status()).toBe('loading')
    await waitFor(() => expect(status()).toBe('authenticated'))
    expect(screen.getByTestId('me')).toHaveTextContent('kari')
  })

  it('is anonymous when there is no session', async () => {
    vi.mocked(authApi.me).mockRejectedValue(new ApiError(401, 'Not logged in', { code: 'auth.not_logged_in' }))
    render(<AuthProvider><Probe /></AuthProvider>)
    await waitFor(() => expect(status()).toBe('anonymous'))
  })

  it('waits for a sleeping server instead of sending a logged-in user to the login page', async () => {
    // Free hosting sleeps when idle: the first requests fail with 502/503 or a network error
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.mocked(authApi.me)
      .mockRejectedValueOnce(new ApiError(0, 'Network error'))
      .mockRejectedValueOnce(new ApiError(503, 'Service Unavailable'))
      .mockRejectedValueOnce(new ApiError(502, 'Bad Gateway'))
      .mockResolvedValue(kari)
    render(<AuthProvider><Probe /></AuthProvider>)
    for (let i = 0; i < 6 && status() !== 'authenticated'; i++) {
      expect(status()).not.toBe('anonymous')
      await act(async () => {
        await vi.advanceTimersByTimeAsync(10_000)
      })
    }
    expect(status()).toBe('authenticated')
    expect(authApi.me).toHaveBeenCalledTimes(4)
  })

  it('gives up waiting eventually and shows the login page', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.mocked(authApi.me).mockRejectedValue(new ApiError(503, 'Service Unavailable'))
    render(<AuthProvider><Probe /></AuthProvider>)
    await act(async () => {
      await vi.advanceTimersByTimeAsync(5 * 60_000)
    })
    expect(status()).toBe('anonymous')
  })

  it('forgets cached data on logout, even if the server cannot be reached', async () => {
    vi.mocked(authApi.me).mockResolvedValue(kari)
    vi.mocked(authApi.logout).mockRejectedValue(new ApiError(0, 'Network error'))
    queryClient.setQueryData(['games', 'mine'], [{ id: 1 }])
    render(<AuthProvider><Probe /></AuthProvider>)
    await waitFor(() => expect(status()).toBe('authenticated'))
    await userEvent.click(screen.getByRole('button', { name: 'out' }))
    await waitFor(() => expect(status()).toBe('anonymous'))
    expect(queryClient.getQueryData(['games', 'mine'])).toBeUndefined()
    expect(screen.getByTestId('me')).toHaveTextContent('-')
  })

  it('signs out everywhere in the app when the session expires', async () => {
    const spy = vi.spyOn(client, 'setSessionExpiredHandler')
    vi.mocked(authApi.me).mockResolvedValue(kari)
    queryClient.setQueryData(['leaderboard'], [1, 2, 3])
    render(<AuthProvider><Probe /></AuthProvider>)
    await waitFor(() => expect(status()).toBe('authenticated'))
    const handler = spy.mock.calls.at(-1)![0]
    act(() => handler())
    expect(status()).toBe('anonymous')
    expect(queryClient.getQueryData(['leaderboard'])).toBeUndefined()
  })
})
