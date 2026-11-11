import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { ApiError, setSessionExpiredHandler } from '../api/client'
import { authApi } from '../api/endpoints'
import { queryClient } from '../api/queryClient'
import type { Me } from '../api/types'

type Status = 'loading' | 'authenticated' | 'anonymous'

interface AuthContextValue {
  status: Status
  me: Me | null
  setMe: (me: Me) => void
  login: (username: string, password: string) => Promise<void>
  register: (body: { username: string; realName: string; password: string; inviteCode: string }) => Promise<void>
  logout: () => Promise<void>
  reload: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

/**
 * The API sleeps when idle (free hosting) and answers 502/503/504, or not at all, for up to about a
 * minute while it wakes. Keep checking the session meanwhile instead of treating a logged-in user as
 * logged out. About two minutes in total, then give up and show the login page.
 */
const WAKE_UP_DELAYS_MS = [2_000, 4_000, 8_000, 15_000, 30_000, 30_000, 30_000]
const isServerAsleep = (e: unknown) => e instanceof ApiError && [0, 502, 503, 504].includes(e.status)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMeState] = useState<Me | null>(null)
  const [status, setStatus] = useState<Status>('loading')

  const setMe = useCallback((m: Me) => {
    setMeState(m)
    setStatus('authenticated')
  }, [])

  const clear = useCallback(() => {
    setMeState(null)
    setStatus('anonymous')
    queryClient.clear()
  }, [])

  const reload = useCallback(
    () =>
      authApi.me().then(setMe, (e: unknown) => {
        if (e instanceof ApiError && (e.status === 401 || e.status === 403)) clear()
        else setStatus((s) => (s === 'loading' ? 'anonymous' : s))
      }),
    [setMe, clear],
  )

  useEffect(() => {
    setSessionExpiredHandler(clear)
    let cancelled = false
    let timer: ReturnType<typeof setTimeout> | undefined
    const check = (attempt: number) => {
      authApi.me().then(
        (m) => {
          if (!cancelled) setMe(m)
        },
        (e: unknown) => {
          if (cancelled) return
          if (isServerAsleep(e) && attempt < WAKE_UP_DELAYS_MS.length) {
            timer = setTimeout(() => check(attempt + 1), WAKE_UP_DELAYS_MS[attempt])
          } else if (e instanceof ApiError && (e.status === 401 || e.status === 403)) {
            clear()
          } else {
            setStatus('anonymous')
          }
        },
      )
    }
    authApi.csrf().catch(() => {})
    check(0)
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [setMe, clear])

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      me,
      setMe,
      reload,
      login: async (username, password) => setMe(await authApi.login(username, password)),
      register: async (body) => setMe(await authApi.register(body)),
      // Signing out locally always works; if the server can't be reached the session simply expires there.
      logout: async () => {
        try {
          await authApi.logout()
        } catch {
          /* offline or server error: still signed out on this device */
        } finally {
          clear()
        }
      },
    }),
    [status, me, setMe, reload, clear],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
