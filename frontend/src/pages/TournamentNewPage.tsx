import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Check } from 'lucide-react'
import { tournamentsApi, usersApi } from '../api/endpoints'
import { invalidateResults } from '../api/queryClient'
import type { TournamentFormat } from '../api/types'
import { useAuth } from '../auth/AuthProvider'
import { Avatar } from '../components/chess/Avatar'
import { PageHeader } from '../components/layout/PageHeader'
import { Button } from '../components/ui/Button'
import { ErrorNotice } from '../components/ui/ErrorNotice'
import { Field, Input } from '../components/ui/Field'
import { Panel } from '../components/ui/Panel'
import { Segmented } from '../components/ui/Segmented'
import { SkeletonRows } from '../components/ui/Skeleton'
import { useToast } from '../components/ui/Toast'
import { useI18n } from '../i18n/I18nProvider'
import { useErrorText } from '../i18n/useErrorText'
import { cn } from '../lib/cn'

const MAX_PLAYERS = 32

export function TournamentNewPage() {
  const { me } = useAuth()
  const { t } = useI18n()
  const tn = t.tournamentNew
  const errorText = useErrorText()
  const navigate = useNavigate()
  const toast = useToast()
  const users = useQuery({ queryKey: ['users'], queryFn: usersApi.list })
  const [name, setName] = useState('')
  const [format, setFormat] = useState<TournamentFormat>('ROUND_ROBIN')
  const [double, setDouble] = useState(false)
  const [selected, setSelected] = useState<Set<number>>(() => new Set([me!.id]))
  const [nameError, setNameError] = useState<string | undefined>()

  const create = useMutation({
    mutationFn: tournamentsApi.create,
    onSuccess: async (res) => {
      await invalidateResults()
      toast(tn.created)
      navigate(`/tournaments/${res.summary.id}`)
    },
    onError: (e) => toast(errorText(e), 'error'),
  })

  const min = format === 'ROUND_ROBIN' ? 3 : 2
  const n = selected.size
  const isDouble = format === 'ROUND_ROBIN' && double
  const rounds = format === 'ROUND_ROBIN' ? (n % 2 ? n : n - 1) * (isDouble ? 2 : 1) : Math.ceil(Math.log2(Math.max(n, 2)))
  const gameCount = format === 'ROUND_ROBIN' ? ((n * (n - 1)) / 2) * (isDouble ? 2 : 1) : Math.max(n - 1, 0)

  function toggle(id: number) {
    setSelected((cur) => {
      const next = new Set(cur)
      if (next.has(id)) next.delete(id)
      else if (next.size < MAX_PLAYERS) next.add(id)
      return next
    })
  }

  function submit(e: FormEvent) {
    e.preventDefault()
    if (!name.trim()) {
      setNameError(tn.nameRequired)
      return
    }
    setNameError(undefined)
    create.mutate({ name: name.trim(), format, doubleRoundRobin: isDouble, participantIds: [...selected] })
  }

  return (
    <div className="mx-auto max-w-2xl">
      <PageHeader title={tn.title} description={tn.description} />
      <form onSubmit={submit} noValidate className="flex flex-col gap-6">
        <Panel className="flex flex-col gap-6 p-5 sm:p-6">
          <Field label={tn.name} error={nameError}>
            {(p) => <Input {...p} value={name} onChange={(e) => setName(e.target.value)} maxLength={80} placeholder={tn.namePlaceholder} />}
          </Field>
          <Segmented<TournamentFormat>
            label={tn.format}
            value={format}
            onChange={setFormat}
            options={[
              { value: 'ROUND_ROBIN', label: t.format.ROUND_ROBIN, hint: tn.roundRobinHint },
              { value: 'SINGLE_ELIMINATION', label: t.format.SINGLE_ELIMINATION, hint: tn.knockoutHint },
            ]}
          />
          {format === 'ROUND_ROBIN' && (
            <label className="flex items-start gap-2.5 text-[0.9375rem]">
              <input type="checkbox" checked={double} onChange={(e) => setDouble(e.target.checked)} className="mt-1 size-4 accent-[var(--c-brand)]" />
              <span>
                {tn.double} <span className="text-muted">{tn.doubleHint}</span>
              </span>
            </label>
          )}
        </Panel>

        <Panel title={tn.players} action={<span className="text-sm text-muted tabular-nums">{tn.selected(n)}</span>}>
          {users.isPending ? (
            <SkeletonRows rows={5} />
          ) : users.isError ? (
            <ErrorNotice error={users.error} onRetry={() => users.refetch()} />
          ) : (
            <ul className="grid gap-1 p-2 sm:grid-cols-2">
              {users.data.map((u) => {
                const on = selected.has(u.id)
                return (
                  <li key={u.id}>
                    <button
                      type="button"
                      aria-pressed={on}
                      onClick={() => toggle(u.id)}
                      className={cn('flex w-full items-center gap-3 rounded-control px-3 py-2.5 text-left transition-colors', on ? 'bg-accent-soft text-ink' : 'text-muted hover:bg-raised')}
                    >
                      <Avatar name={u.realName} seed={u.username} size="sm" />
                      <span className="flex-1 truncate">{u.realName}</span>
                      <span className="text-[0.8125rem] text-faint tabular-nums">{u.rating}</span>
                      <span className={cn('flex size-5 items-center justify-center rounded-md border', on ? 'border-brand bg-brand text-on-accent' : 'border-line bg-surface')}>
                        {on && <Check aria-hidden className="size-3.5" />}
                      </span>
                    </button>
                  </li>
                )
              })}
            </ul>
          )}
        </Panel>

        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-[0.9375rem] text-muted" aria-live="polite">
            {n >= min ? tn.summary(rounds, gameCount, format === 'SINGLE_ELIMINATION') : tn.needMore(min)}
          </p>
          <Button type="submit" size="lg" loading={create.isPending}>
            {tn.submit}
          </Button>
        </div>
      </form>
    </div>
  )
}
