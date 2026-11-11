import { useState, type ChangeEvent, type FormEvent } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthProvider'
import { Button } from '../components/ui/Button'
import { Field, Input } from '../components/ui/Field'
import { useI18n } from '../i18n/I18nProvider'
import { useErrorText, useFieldErrors } from '../i18n/useErrorText'
import { AuthLayout } from './AuthLayout'

type Form = { inviteCode: string; username: string; realName: string; password: string }

export function RegisterPage() {
  const { register, status } = useAuth()
  const { t } = useI18n()
  const errorText = useErrorText()
  const fieldErrors = useFieldErrors()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [form, setForm] = useState<Form>({ inviteCode: params.get('code') ?? '', username: '', realName: '', password: '' })
  const [errors, setErrors] = useState<Partial<Record<keyof Form, string>>>({})
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  if (status === 'authenticated') return <Navigate to="/" replace />

  const r = t.auth.register
  const set = (k: keyof Form) => (e: ChangeEvent<HTMLInputElement>) => setForm((f) => ({ ...f, [k]: e.target.value }))

  async function submit(e: FormEvent) {
    e.preventDefault()
    const local: Partial<Record<keyof Form, string>> = {}
    if (!/^[A-Za-z0-9_]{3,20}$/.test(form.username.trim())) local.username = r.errUsername
    if (!form.realName.trim()) local.realName = r.errRealName
    if (form.password.length < 10) local.password = r.errPassword
    if (!form.inviteCode.trim()) local.inviteCode = r.errInvite
    setErrors(local)
    setError(null)
    if (Object.keys(local).length) return

    setBusy(true)
    try {
      await register({ ...form, username: form.username.trim(), realName: form.realName.trim(), inviteCode: form.inviteCode.trim() })
      navigate('/', { replace: true })
    } catch (err) {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length) setErrors(fieldErrors(err))
      else if (err instanceof ApiError && err.code === 'invite.invalid') setErrors({ inviteCode: errorText(err) })
      else if (err instanceof ApiError && err.code === 'auth.username_taken') setErrors({ username: errorText(err) })
      else setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthLayout
      title={r.title}
      subtitle={r.subtitle}
      footer={
        <>
          {r.haveAccount}{' '}
          <Link to="/login" className="font-medium text-accent hover:underline">
            {r.logIn}
          </Link>
        </>
      }
    >
      <form onSubmit={submit} className="flex flex-col gap-5" noValidate>
        <Field label={r.inviteCode} error={errors.inviteCode}>
          {(p) => <Input {...p} value={form.inviteCode} onChange={set('inviteCode')} autoCapitalize="characters" spellCheck={false} placeholder="K7QX4-MZP2R" />}
        </Field>
        <Field label={r.realName} hint={r.realNameHint} error={errors.realName}>
          {(p) => <Input {...p} value={form.realName} onChange={set('realName')} autoComplete="name" maxLength={60} />}
        </Field>
        <Field label={r.username} hint={r.usernameHint} error={errors.username}>
          {(p) => <Input {...p} value={form.username} onChange={set('username')} autoComplete="username" autoCapitalize="none" spellCheck={false} maxLength={20} />}
        </Field>
        <Field label={r.password} hint={r.passwordHint} error={errors.password}>
          {(p) => <Input {...p} type="password" value={form.password} onChange={set('password')} autoComplete="new-password" maxLength={72} />}
        </Field>
        {error !== null && (
          <p role="alert" className="text-[0.9375rem] text-accent">
            {errorText(error)}
          </p>
        )}
        <Button type="submit" size="lg" loading={busy}>
          {r.submit}
        </Button>
      </form>
    </AuthLayout>
  )
}
