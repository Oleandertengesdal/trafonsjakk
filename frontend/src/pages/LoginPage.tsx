import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import { useAuth } from '../auth/AuthProvider'
import { Button } from '../components/ui/Button'
import { Field, Input } from '../components/ui/Field'
import { useI18n } from '../i18n/I18nProvider'
import { useErrorText } from '../i18n/useErrorText'
import { safeAppPath } from '../lib/safePath'
import { AuthLayout } from './AuthLayout'

export function LoginPage() {
  const { login, status } = useAuth()
  const { t } = useI18n()
  const errorText = useErrorText()
  const navigate = useNavigate()
  const location = useLocation()
  const from = safeAppPath((location.state as { from?: unknown } | null)?.from) ?? '/'
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  if (status === 'authenticated') return <Navigate to={from} replace />

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!username.trim() || !password) return
    setBusy(true)
    setError(null)
    try {
      await login(username.trim(), password)
      navigate(from, { replace: true })
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthLayout
      title={t.auth.login.title}
      subtitle={t.auth.login.subtitle}
      footer={
        <>
          {t.auth.login.newHere}{' '}
          <Link to="/register" className="font-medium text-accent hover:underline">
            {t.auth.login.createAccount}
          </Link>
        </>
      }
    >
      <form onSubmit={submit} className="flex flex-col gap-5" noValidate>
        <Field label={t.auth.login.username}>
          {(p) => <Input {...p} autoComplete="username" autoCapitalize="none" spellCheck={false} value={username} onChange={(e) => setUsername(e.target.value)} required />}
        </Field>
        <Field label={t.auth.login.password}>
          {(p) => <Input {...p} type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />}
        </Field>
        {error !== null && (
          <p role="alert" className="text-[0.9375rem] text-accent">
            {errorText(error)}
          </p>
        )}
        <Button type="submit" size="lg" loading={busy} disabled={!username.trim() || !password}>
          {t.auth.login.submit}
        </Button>
      </form>
    </AuthLayout>
  )
}
