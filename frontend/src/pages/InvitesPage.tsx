import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Copy, Ticket } from 'lucide-react'
import { invitesApi } from '../api/endpoints'
import { queryClient } from '../api/queryClient'
import { useAuth } from '../auth/AuthProvider'
import { PageHeader } from '../components/layout/PageHeader'
import { Button } from '../components/ui/Button'
import { Field, Input, Select } from '../components/ui/Field'
import { EmptyState } from '../components/ui/EmptyState'
import { ErrorNotice } from '../components/ui/ErrorNotice'
import { Panel } from '../components/ui/Panel'
import { SkeletonRows } from '../components/ui/Skeleton'
import { useToast } from '../components/ui/Toast'
import { useI18n } from '../i18n/I18nProvider'
import { useErrorText } from '../i18n/useErrorText'
import { cn } from '../lib/cn'

export function InvitesPage() {
  const { me } = useAuth()
  const { t, f } = useI18n()
  const inv = t.invites
  const errorText = useErrorText()
  const toast = useToast()
  const q = useQuery({ queryKey: ['invites'], queryFn: invitesApi.list })
  const [fresh, setFresh] = useState<{ code: string; expiresAt: string; maxUses: number } | null>(null)
  const isAdmin = me!.role === 'ADMIN'
  // Admin options: several people, own lifetime
  const [people, setPeople] = useState('1')
  const [hours, setHours] = useState(168)
  const peopleNumber = Number(people)
  const peopleValid = Number.isInteger(peopleNumber) && peopleNumber >= 1 && peopleNumber <= 500
  const [triedInvalid, setTriedInvalid] = useState(false)

  const create = useMutation({
    mutationFn: (opts: { maxUses?: number; validHours?: number }) => invitesApi.create(opts),
    onSuccess: (r) => {
      setFresh(r)
      void queryClient.invalidateQueries({ queryKey: ['invites'] })
    },
    onError: (e) => toast(errorText(e), 'error'),
  })
  const revoke = useMutation({
    mutationFn: invitesApi.revoke,
    onSuccess: () => {
      toast(inv.revoked)
      void queryClient.invalidateQueries({ queryKey: ['invites'] })
    },
    onError: (e) => toast(errorText(e), 'error'),
  })

  const link = fresh ? `${window.location.origin}/register?code=${encodeURIComponent(fresh.code)}` : ''
  async function copy(text: string) {
    try {
      await navigator.clipboard.writeText(text)
      toast(t.common.copied)
    } catch {
      toast(t.common.copyFailed, 'error')
    }
  }

  function createAdminCode() {
    if (!peopleValid) {
      setTriedInvalid(true)
      return
    }
    setTriedInvalid(false)
    create.mutate({ maxUses: peopleNumber, validHours: hours })
  }

  const durations: [number, string][] = [
    [1, inv.durations.h1],
    [24, inv.durations.d1],
    [72, inv.durations.d3],
    [168, inv.durations.w1],
    [336, inv.durations.w2],
    [720, inv.durations.d30],
    [2160, inv.durations.d90],
  ]

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        title={inv.title}
        description={isAdmin ? inv.adminDescription : inv.description}
        action={
          isAdmin ? undefined : (
            <Button onClick={() => create.mutate({})} loading={create.isPending}>
              <Ticket aria-hidden className="size-4.5" /> {inv.create}
            </Button>
          )
        }
      />

      {isAdmin && (
        <Panel title={inv.newCode} className="mb-6">
          <form
            noValidate
            className="grid gap-4 px-5 pt-2 pb-5 sm:grid-cols-[minmax(13rem,auto)_1fr_auto] sm:items-start"
            onSubmit={(e) => {
              e.preventDefault()
              createAdminCode()
            }}
          >
            <Field label={inv.howMany} hint={inv.howManyHint} error={triedInvalid && !peopleValid ? inv.howManyHint : undefined}>
              {(p) => (
                <Input {...p} type="number" inputMode="numeric" min={1} max={500} value={people} onChange={(e) => setPeople(e.target.value)} />
              )}
            </Field>
            <Field label={inv.validFor}>
              {(p) => (
                <Select {...p} value={hours} onChange={(e) => setHours(Number(e.target.value))}>
                  {durations.map(([h, label]) => (
                    <option key={h} value={h}>
                      {label}
                    </option>
                  ))}
                </Select>
              )}
            </Field>
            <Button type="submit" loading={create.isPending} className="sm:mt-[1.625rem] sm:h-11">
              <Ticket aria-hidden className="size-4.5" /> {inv.create}
            </Button>
          </form>
        </Panel>
      )}

      {fresh && (
        <Panel className="rise mb-6 border-accent/50 p-5">
          <p className="text-[0.9375rem] text-muted">{inv.freshIntro}</p>
          <p className="mt-2 font-display text-[2rem] font-bold tracking-[0.06em] break-all select-all">{fresh.code}</p>
          <div className="mt-4 flex flex-wrap gap-2">
            <Button variant="secondary" size="sm" onClick={() => copy(fresh.code)}>
              <Copy aria-hidden className="size-4" /> {inv.copyCode}
            </Button>
            <Button variant="secondary" size="sm" onClick={() => copy(link)}>
              <Copy aria-hidden className="size-4" /> {inv.copyLink}
            </Button>
          </div>
          <p className="mt-3 text-[0.8125rem] text-faint">
            {inv.forPeople(fresh.maxUses)} {inv.expires(f.dateTime(fresh.expiresAt))}
          </p>
        </Panel>
      )}

      <Panel title={me!.role === 'ADMIN' ? inv.allCodes : inv.yourCodes}>
        {q.isPending ? (
          <SkeletonRows rows={3} />
        ) : q.isError ? (
          <ErrorNotice error={q.error} onRetry={() => q.refetch()} />
        ) : q.data.length === 0 ? (
          <EmptyState icon={<Ticket />} title={inv.emptyTitle}>
            {inv.emptyBody}
          </EmptyState>
        ) : (
          <ul className="divide-y divide-line">
            {q.data.map((i) => (
              <li key={i.id} className="flex flex-wrap items-center justify-between gap-3 px-5 py-3.5">
                <div className="min-w-0">
                  <p className={cn('font-medium', i.status === 'ACTIVE' ? 'text-win' : 'text-muted')}>
                    {i.status === 'ACTIVE' && i.maxUses > 1 ? inv.activeMulti : inv.status[i.status]}
                    {i.maxUses > 1 && <span className="font-normal text-muted"> ({inv.usedOf(i.useCount, i.maxUses)})</span>}
                  </p>
                  <p className="text-[0.8125rem] text-muted">
                    {i.maxUses === 1 && i.uses.length === 1
                      ? inv.joined(i.uses[0].player.realName, f.relative(i.uses[0].usedAt))
                      : i.status === 'ACTIVE'
                        ? inv.expiresIn(f.relative(i.expiresAt))
                        : inv.created(f.date(i.createdAt))}
                    {isAdmin && i.createdBy && inv.createdBy(i.createdBy.realName)}
                  </p>
                  {i.maxUses > 1 && i.uses.length > 0 && (
                    <p className="text-[0.8125rem] text-muted">{inv.joinedList(i.uses.map((u) => u.player.realName).join(', '))}</p>
                  )}
                </div>
                {i.status === 'ACTIVE' && (
                  <Button variant="ghost" size="sm" onClick={() => revoke.mutate(i.id)} loading={revoke.isPending && revoke.variables === i.id}>
                    {inv.revoke}
                  </Button>
                )}
              </li>
            ))}
          </ul>
        )}
      </Panel>
    </div>
  )
}
