import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { authApi } from '../api/endpoints'
import { useAuth } from '../auth/AuthProvider'
import { Button } from '../components/ui/Button'
import { Field, Input } from '../components/ui/Field'
import { useI18n } from '../i18n/I18nProvider'
import { useErrorText, useFieldErrors } from '../i18n/useErrorText'
import { AuthLayout } from './AuthLayout'

/** Opened from the one-time link an admin sent: pick a new password, then you're logged in. */
export function ResetPasswordPage() {
  const { t } = useI18n()
  const r = t.reset
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const navigate = useNavigate()
  const { setMe } = useAuth()
  const errorText = useErrorText()
  const fieldErrors = useFieldErrors()
  const [password, setPassword] = useState('')
  const [repeat, setRepeat] = useState('')
  const [errors, setErrors] = useState<{ password?: string; repeat?: string; form?: string }>({})
  const [usedUp, setUsedUp] = useState(false)

  const target = useQuery({ queryKey: ['reset', token], queryFn: () => authApi.checkReset(token), enabled: token !== '', retry: false })

  const save = useMutation({
    mutationFn: () => authApi.resetPassword(token, password),
    onSuccess: (me) => {
      setMe(me)
      // replace: the token shouldn't stay in the browser history
      navigate('/', { replace: true })
    },
    onError: (e) => {
      if (e instanceof ApiError && e.code === 'reset.invalid') setUsedUp(true)
      else if (e instanceof ApiError && e.fieldCodes.newPassword) setErrors({ password: fieldErrors(e).newPassword })
      else setErrors({ form: errorText(e) })
    },
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    const next: typeof errors = {}
    if (password.length < 10) next.password = r.tooShort
    else if (password !== repeat) next.repeat = r.mismatch
    setErrors(next)
    if (Object.keys(next).length === 0) save.mutate()
  }

  const invalid = token === '' || usedUp || (target.isError && target.error instanceof ApiError && target.error.code === 'reset.invalid')
  if (invalid) {
    return (
      <AuthLayout
        title={r.invalidTitle}
        subtitle={r.invalidBody}
        footer={
          <Link to="/login" className="font-medium text-accent hover:underline">
            {r.toLogin}
          </Link>
        }
      >
        {null}
      </AuthLayout>
    )
  }

  return (
    <AuthLayout
      title={r.title}
      subtitle={target.data ? r.subtitle(target.data.realName, target.data.username) : r.checking}
      footer={
        <Link to="/login" className="font-medium text-accent hover:underline">
          {r.toLogin}
        </Link>
      }
    >
      {target.isError ? (
        <p role="alert" className="text-accent">
          {errorText(target.error)}
        </p>
      ) : (
        <form onSubmit={submit} noValidate className="flex flex-col gap-4">
          <Field label={r.newPassword} hint={r.hint} error={errors.password}>
            {(p) => (
              <Input {...p} type="password" autoComplete="new-password" value={password} maxLength={72} onChange={(e) => setPassword(e.target.value)} />
            )}
          </Field>
          <Field label={r.repeat} error={errors.repeat}>
            {(p) => <Input {...p} type="password" autoComplete="new-password" value={repeat} maxLength={72} onChange={(e) => setRepeat(e.target.value)} />}
          </Field>
          {errors.form && (
            <p role="alert" className="text-[0.9375rem] text-accent">
              {errors.form}
            </p>
          )}
          <Button type="submit" size="lg" loading={save.isPending} disabled={!target.data}>
            {r.submit}
          </Button>
        </form>
      )}
    </AuthLayout>
  )
}
