import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { LogoMark } from '../components/brand/LogoMark'
import { useI18n } from '../i18n/I18nProvider'
import { useAuth } from './AuthProvider'

export function RequireAuth({ children }: { children: ReactNode }) {
  const { status } = useAuth()
  const location = useLocation()
  if (status === 'loading') return <Splash />
  if (status === 'anonymous') return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  return <>{children}</>
}

export function RequireAdmin({ children }: { children: ReactNode }) {
  const { me } = useAuth()
  if (me?.role !== 'ADMIN') return <Navigate to="/" replace />
  return <>{children}</>
}

function Splash() {
  const { t } = useI18n()
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-3" role="status">
      <LogoMark className="size-12 animate-pulse" />
      <p className="text-sm text-muted">{t.splash}</p>
    </div>
  )
}
