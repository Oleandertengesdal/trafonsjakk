import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Download, Moon, Sun, Trash2 } from 'lucide-react'
import { usersApi } from '../api/endpoints'
import { invalidateResults } from '../api/queryClient'
import { useAuth } from '../auth/AuthProvider'
import { PageHeader } from '../components/layout/PageHeader'
import { Button } from '../components/ui/Button'
import { Dialog } from '../components/ui/Dialog'
import { Field, Input } from '../components/ui/Field'
import { LanguageSwitch } from '../components/ui/LanguageSwitch'
import { Panel } from '../components/ui/Panel'
import { useToast } from '../components/ui/Toast'
import { useI18n } from '../i18n/I18nProvider'
import { useErrorText } from '../i18n/useErrorText'
import { getTheme, setTheme, type Theme } from '../lib/theme'

export function SettingsPage() {
  const { me, setMe, logout } = useAuth()
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

  const [deleting, setDeleting] = useState(false)
  const [deletePassword, setDeletePassword] = useState('')
  const download = useMutation({
    mutationFn: usersApi.exportData,
    onSuccess: (data) => {
      const url = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' }))
      const a = document.createElement('a')
      a.href = url
      a.download = `trafonsjakk-${me!.username}.json`
      a.click()
      window.setTimeout(() => URL.revokeObjectURL(url), 10_000)
      toast(st.downloaded)
    },
    onError: (e) => toast(errorText(e), 'error'),
  })
  const deleteAccount = useMutation({
    mutationFn: () => usersApi.deleteAccount(deletePassword),
    onSuccess: async () => {
      toast(st.deleted)
      await logout()
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

      <Panel title={st.dataTitle}>
        <div className="flex flex-col items-start gap-4 px-5 pt-1 pb-5">
          <p className="text-[0.9375rem] text-muted">{st.dataBody}</p>
          <Button variant="secondary" loading={download.isPending} onClick={() => download.mutate()}>
            <Download aria-hidden className="size-4.5" />
            {st.download}
          </Button>
        </div>
      </Panel>

      <Panel title={st.deleteTitle}>
        <div className="flex flex-col items-start gap-4 px-5 pt-1 pb-5">
          <p className="text-[0.9375rem] text-muted">{st.deleteBody}</p>
          <Button variant="danger" onClick={() => setDeleting(true)}>
            <Trash2 aria-hidden className="size-4.5" />
            {st.deleteButton}
          </Button>
        </div>
      </Panel>

      <Dialog
        open={deleting}
        onClose={() => {
          if (deleteAccount.isPending) return
          setDeleting(false)
          setDeletePassword('')
        }}
        title={st.deleteConfirmTitle}
      >
        <form
          className="flex flex-col gap-4"
          onSubmit={(e: FormEvent) => {
            e.preventDefault()
            if (deletePassword) deleteAccount.mutate()
          }}
        >
          <p className="text-[0.9375rem] text-muted">{st.deleteConfirmBody}</p>
          <Field label={st.deletePassword}>
            {(p) => (
              <Input {...p} type="password" autoComplete="current-password" value={deletePassword} onChange={(e) => setDeletePassword(e.target.value)} />
            )}
          </Field>
          <div className="mt-2 flex justify-end gap-2">
            <Button
              type="button"
              variant="secondary"
              disabled={deleteAccount.isPending}
              onClick={() => {
                setDeleting(false)
                setDeletePassword('')
              }}
            >
              {st.cancel}
            </Button>
            <Button type="submit" variant="danger" loading={deleteAccount.isPending} disabled={!deletePassword}>
              {st.deleteFinal}
            </Button>
          </div>
        </form>
      </Dialog>
    </div>
  )
}
