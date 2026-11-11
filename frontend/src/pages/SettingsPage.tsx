import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Moon, Sun } from 'lucide-react'
import { usersApi } from '../api/endpoints'
import { invalidateResults } from '../api/queryClient'
import { useAuth } from '../auth/AuthProvider'
import { PageHeader } from '../components/layout/PageHeader'
import { Button } from '../components/ui/Button'
import { Field, Input } from '../components/ui/Field'
import { LanguageSwitch } from '../components/ui/LanguageSwitch'
import { Panel } from '../components/ui/Panel'
import { useToast } from '../components/ui/Toast'
import { useI18n } from '../i18n/I18nProvider'
import { useErrorText } from '../i18n/useErrorText'
import { getTheme, setTheme, type Theme } from '../lib/theme'

export function SettingsPage() {
  const { me, setMe } = useAuth()
  const { t } = useI18n()
  const st = t.settings
  const errorText = useErrorText()
  const toast = useToast()
  const [realName, setRealName] = useState(me!.realName)
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [theme, setThemeState] = useState<Theme>(getTheme)

  const saveName = useMutation({
    mutationFn: () => usersApi.updateMe(realName.trim()),
    onSuccess: async (m) => {
      setMe(m)
      await invalidateResults()
      toast(st.nameSaved)
    },
    onError: (e) => toast(errorText(e), 'error'),
  })
  const savePassword = useMutation({
    mutationFn: () => usersApi.changePassword(current, next),
    onSuccess: () => {
      setCurrent('')
      setNext('')
      toast(st.passwordChanged)
    },
    onError: (e) => toast(errorText(e), 'error'),
  })

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6">
      <PageHeader title={st.title} />

      <Panel title={st.nameTitle}>
        <form
          className="flex flex-col gap-4 px-5 pt-2 pb-5 sm:flex-row sm:items-start"
          onSubmit={(e: FormEvent) => {
            e.preventDefault()
            if (realName.trim() && realName.trim() !== me!.realName) saveName.mutate()
          }}
        >
          <div className="flex-1">
            <Field label={st.nameLabel} hint={st.nameHint(me!.username)}>
              {(p) => <Input {...p} value={realName} maxLength={60} onChange={(e) => setRealName(e.target.value)} />}
            </Field>
          </div>
          <Button type="submit" variant="secondary" loading={saveName.isPending} disabled={!realName.trim() || realName.trim() === me!.realName} className="sm:mt-[1.875rem]">
            {st.saveName}
          </Button>
        </form>
      </Panel>

      <Panel title={st.passwordTitle}>
        <form
          className="flex flex-col gap-4 px-5 pt-2 pb-5"
          onSubmit={(e: FormEvent) => {
            e.preventDefault()
            if (current && next.length >= 10) savePassword.mutate()
          }}
        >
          <Field label={st.currentPassword}>
            {(p) => <Input {...p} type="password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} />}
          </Field>
          <Field label={st.newPassword} hint={st.newPasswordHint}>
            {(p) => <Input {...p} type="password" autoComplete="new-password" value={next} maxLength={72} onChange={(e) => setNext(e.target.value)} />}
          </Field>
          <div>
            <Button type="submit" variant="secondary" loading={savePassword.isPending} disabled={!current || next.length < 10}>
              {st.changePassword}
            </Button>
          </div>
        </form>
      </Panel>

      <Panel title={t.language.label}>
        <div className="px-5 pt-2 pb-5">
          <LanguageSwitch />
        </div>
      </Panel>

      <Panel title={t.theme.label}>
        <div role="group" aria-label={t.theme.label} className="flex gap-2 px-5 pt-2 pb-5">
          {(['light', 'dark'] as const).map((th) => (
            <Button
              key={th}
              variant={theme === th ? 'primary' : 'secondary'}
              onClick={() => {
                setTheme(th)
                setThemeState(th)
              }}
              aria-pressed={theme === th}
            >
              {th === 'dark' ? <Moon aria-hidden className="size-4.5" /> : <Sun aria-hidden className="size-4.5" />}
              {th === 'dark' ? t.theme.dark : t.theme.light}
            </Button>
          ))}
        </div>
      </Panel>
    </div>
  )
}
